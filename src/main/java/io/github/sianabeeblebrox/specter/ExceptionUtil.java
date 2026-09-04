package io.github.sianabeeblebrox.specter;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Supplier;

/**
 * Utility methods and types for working with exceptions and nulls
 */
@ParametersAreNonnullByDefault
public final class ExceptionUtil {
    /**
     * Throws a {@link java.lang.Throwable} without having to declare it in a checked {@code throws} declaration
     * (Also useful for throwing as an expression)
     * @param throwable the value to throw
     * @return <i>never</i>
     * @param <T>
     * @param <R>
     * @throws T the value to throw
     */
    @SuppressWarnings("unchecked")
    public static <T extends Throwable, R> R throwUnchecked(final Throwable throwable) throws T {
        throw (T) throwable;
    }

    /**
     * Forces checking a checked exception that the compiler does not already see
     * (Does not throw by itself, used to undo the results of {@link #unchecked(CheckedCode)})
     * @param type the type of the exception to check
     * @param value an expression to wrap
     * @return {@code value}
     * @param <T>
     * @param <E>
     * @throws E the checked exception
     */
    public static <T, E extends Throwable> T checked(final Class<E> type, final T value) throws E {
        return value;
    }

    /**
     * Forces checking a checked exception that the compiler does not already see
     * (Does not throw by itself, used to undo the results of {@link #unchecked(CheckedCode)})
     * @param type the type of the exception to check
     * @param <E>
     * @throws E the checked exception
     */
    public static <E extends Throwable> void checked(final Class<E> type) throws E {}

    /**
     * A functional interface for a value-producing block of code with optional checked exceptions
     * @param <T> the return type of the block of code
     */
    @FunctionalInterface
    public interface CheckedCode<T> {
        T evaluate() throws Throwable;
    }

    /**
     * A functional interface for a void block of code with optional checked exceptions
     */
    @FunctionalInterface
    public interface VoidCheckedCode {
        void evaluate() throws Throwable;
    }

    /**
     * A {@link java.util.function.Function} but with optional checked exceptions
     * @param <T> the type of the input to the function
     * @param <R> the type of the result of the function
     * @param <E> the type of the checked exceptions
     */
    @FunctionalInterface
    public interface CheckedFunction<T,R,E extends Throwable> {
        R apply(final T t) throws E;
    }

    /**
     * A {@link java.util.function.Supplier} but with optional checked exceptions
     * @param <R> the type of the result supplied by this supplier
     * @param <E> the type of the checked exceptions
     */
    @FunctionalInterface
    public interface CheckedSupplier<R,E extends Throwable> {
        R get() throws E;
    }

    /**
     * A {@link java.util.function.Consumer} but with optional checked exceptions
     * @param <T> the type of the input to the operation
     * @param <E> the type of the checked exceptions
     */
    @FunctionalInterface
    public interface CheckedConsumer<T,E extends Throwable> {
        void accept(final T t) throws E;
    }

    /**
     * Wraps a value-producing block of code that would otherwise have to declare a checked exception
     * <i>(Note: this only affects compile time behavior, exceptions may still be thrown unchecked at runtime!)</i>
     * @param code the block of code (as a lambda)
     * @return the return value of the block of code
     * @param <T>
     */
    public static <T> T unchecked(final CheckedCode<T> code) {
        try {
            return code.evaluate();
        } catch(final Throwable t) {
            return ExceptionUtil.throwUnchecked(t);
        }
    }

    /**
     * Wraps a void block of code that would otherwise have to declare a checked exception
     * <i>(Note: this only affects compile time behavior, exceptions may still be thrown unchecked at runtime!)</i>
     * @param code the block of code (as a lambda)
     */
    public static void unchecked(final VoidCheckedCode code)  {
        try {
            code.evaluate();
        } catch(final Throwable t) {
            ExceptionUtil.throwUnchecked(t);
        }
    }

    /**
     * A value-producing try-with-resources block that treats exceptions as unchecked
     * <i>(Note: this only affects compile time behavior, exceptions may still be thrown unchecked at runtime!)</i>
     * @param with a supplier (with optional checked exceptions) providing a {@link java.lang.AutoCloseable} to run {@code code} with
     * @param code a function (with optional checked exceptions) that operators on the resource
     * @return the return value of {@code code}
     * @param <T>
     * @param <R>
     * @param <E1>
     * @param <E2>
     */
    public static <T extends AutoCloseable, R, E1 extends Throwable, E2 extends Throwable> R with(final CheckedSupplier<T, E1> with, final CheckedFunction<T,R,E2> code) {
        try(final T t = with.get()) {
            return code.apply(t);
        } catch(final Throwable e) {
            return ExceptionUtil.throwUnchecked(e);
        }
    }

    /**
     * A void try-with-resources block that treats exceptions as unchecked
     * <i>(Note: this only affects compile time behavior, exceptions may still be thrown unchecked at runtime!)</i>
     * @param with a supplier (with optional checked exceptions) providing a {@link java.lang.AutoCloseable} to run {@code code} with
     * @param code a consumer (with optional checked exceptions) that operators on the resource
     * @param <T>
     * @param <E1>
     * @param <E2>
     */
    public static <T extends AutoCloseable, E1 extends Throwable, E2 extends Throwable> void with(final CheckedSupplier<T, E1> with, final CheckedConsumer<T,E2> code) {
        try(final T t = with.get()) {
            code.accept(t);
        } catch(final Throwable e) {
            ExceptionUtil.throwUnchecked(e);
        }
    }

    /**
     * Ignores all exceptions within a value-producing block of code
     * @param code the block of code (as a lambda)
     * @param fallback a {@link Supplier} producing a fallback value to return if an exception occurs
     * @return the return value of the block of code or {@code fallback} if an exception occurred
     * @param <T>
     */
    public static <T> T ignored(final CheckedCode<T> code, final Supplier<T> fallback) {
        try {
            return code.evaluate();
        } catch (final Throwable e) {
            return fallback.get();
        }
    }

    /**
     * Ignores all exceptions within a value-producing block of code
     * @param code the block of code (as a lambda)
     * @return the return value of the block of code or {@code null} if an exception occurred
     * @param <T>
     */
    public static <T> T ignored(final CheckedCode<T> code) {
        return ignored(code, () -> null);
    }

    /**
     * Ignores all exceptions within a void block of code
     * @param code the block of code (as a lambda)
     */
    public static void ignored(final VoidCheckedCode code) {
        try {
            code.evaluate();
        } catch(final Throwable ignored) {}
    }

    /**
     * Null-coalesce
     * @param args the values to coalesce
     * @return the first non-null value in {@code args} iff any else {@code null}
     * @param <T>
     */
    public static <T> @Nullable T ncls(final @Nullable T... args) {
        for(final T t : args) if(t != null) return t;
        return null;
    }

    /**
     * Lazy null-coalesce
     * @param t the first value to coalesce
     * @param args subsequent values to coalesce, only evaluated as needed
     * @return the first non-null value in {@code {t, ...args}} iff any else {@code null}
     * @param <T>
     */
    @SafeVarargs
    public static <T> @Nullable T ncls(@Nullable T t, final Supplier<T>... args) {
        if(t != null) return t;
        for(final Supplier<T> s : args) if((t = s.get()) != null) return t;
        return null;
    }

    /**
     * Reinterpret a {@link Nullable} value as {@link Nonnull} <i>without</i> doing any assertion
     * @param t the value
     * @return {@code t}
     * @param <T>
     */
    @SuppressWarnings("ConstantConditions")
    public static <T> @Nonnull T nonnull(final @Nullable T t) {
        return t;
    }

    /**
     * Reinterpret a value as {@link Nullable}
     * @param t the value
     * @return {@code t}
     * @param <T>
     */
    public static <T> @Nullable T nullable(final @Nullable T t) {
        return t;
    }
}
