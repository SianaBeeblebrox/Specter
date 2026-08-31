package io.github.sianabeeblebrox.specter;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;

import static io.github.sianabeeblebrox.specter.ExceptionUtil.unchecked;

/**
 * Utility methods for dynamic reflection
 */
@SuppressWarnings("unchecked")
@ParametersAreNonnullByDefault
public final class Dynamics {
    // TODO field set, array constructor

    private static @Nullable Field getField(Class<?> clazz, final String name) {
        while(clazz != null) {
            try {
                return clazz.getDeclaredField(name);
            } catch(final Throwable t) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    private static @Nullable Method getMethod(Class<?> clazz, final String name, final Class<?>... parameterTypes) {
        while(clazz != null) {
            try {
                return clazz.getDeclaredMethod(name, parameterTypes);
            } catch(final Throwable t) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    private static <T> @Nullable Constructor<?> getConstructor(Class<?> clazz, final Class<?>... parameterTypes) {
        while(clazz != null) {
            try {
                return clazz.getDeclaredConstructor(parameterTypes);
            } catch(final Throwable t) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    private static <T extends AccessibleObject> T setAccessible(final T t) {
        t.setAccessible(true);
        return t;
    }

    private static MethodHandle getMethodHandle(final Class<?> clazz, final boolean allowInit, final String name, final Class<?>... parameterTypes) {
        if(allowInit && name.equals("<init>")) {
            return unchecked(() -> MethodHandles.lookup().unreflectConstructor(setAccessible(getConstructor(clazz, parameterTypes))));
        } else {
            return unchecked(() -> MethodHandles.lookup().unreflect(setAccessible(getMethod(clazz, name, parameterTypes))));
        }
    }

    /**
     * Gets static field bypassing access checks
     * (e.g., {@code Foo.bar} &rarr; {@code get(Foo.class, "bar")})
     * <p><i>Note: In the event you need to get an instance field on a {@code Class<?>} value, use an explicit
     * {@code (Object)} cast to use the {@link #get(Object, String)} overload instead.</i></p>
     * @param clazz the owning class
     * @param field the field name
     * @return the field value
     * @param <T>
     */
    public static <T> T get(final Class<?> clazz, final String field) {
        return (T) unchecked(() -> setAccessible(getField(clazz, field)).get(null));
    }

    /**
     * Gets instance field bypassing access checks
     * (e.g., {@code foo.bar} &rarr; {@code get(foo, "bar")})
     * @param instance the instance
     * @param field the field name
     * @return the field value
     * @param <T>
     */
    public static <T> T get(final Object instance, final String field) {
        if(field.equals("length") && instance.getClass().isArray()) {
            return (T) (Object) Array.getLength(instance);
        }
        return (T) unchecked(() -> setAccessible(getField(instance.getClass(), field)).get(instance));
    }

    /**
     * Gets array element
     * (e.g., {@code arr[n]} &rarr; {@code get(arr, n)})
     * @param instance the array instance
     * @param index the index
     * @return the value at the given index
     * @param <T>
     */
    public static <T> T get(final Object instance, final int index) {
        return (T) Array.get(instance, index);
    }

    /**
     * Sets an array element
     * @param instance the array instance
     * @param index the index
     * @param value the new value
     * @param <T>
     */
    public static <T> void set(final Object instance, final int index, final @Nullable T value) {
        Array.set(instance, index, value);
    }

    /**
     * Invokes static method bypassing access checks
     * (e.g., {@code Foo.bar(3, "baz")} &rarr; {@code invoke(Foo.class, "bar", int.class, String.class, 3, "bar")})
     * <p><i>Note: In the event you need to call an instance method on a {@code Class<?>} value, use an explicit
     * {@code (Object)} cast to use the {@link #invoke(Object, String, Object...)} overload instead.</i></p>
     * @param clazz the owning class
     * @param name the method name
     * @param args the parameter types then argument values
     * @return the return value of the method (or null if void)
     * @param <T>
     */
    public static <T> T invoke(final Class<?> clazz, final String name, final Object... args) {
        assert args.length % 2 == 0;


        final Class<?>[] parameterTypes = new Class[args.length/2];
        System.arraycopy(args, 0, parameterTypes, 0, args.length/2);

        final Object[] argValues = new Object[args.length/2];
        System.arraycopy(args, args.length/2, argValues, 0, args.length/2);

        return (T) unchecked(() -> getMethodHandle(clazz, true, name, parameterTypes).invokeWithArguments(argValues));
    }

    /**
     * Invokes instance method bypassing access checks
     * (e.g., {@code foo.bar(3, "baz")} &rarr; {@code invoke(foo, "bar", int.class, String.class, 3, "bar")})
     * @param instance the instance
     * @param name the method name
     * @param args the parameter types then argument values
     * @return the return value of the method (or null if void)
     * @param <T>
     */
    public static <T> T invoke(final Object instance, final String name, final Object... args) {
        assert args.length % 2 == 0;

        final Class<?>[] parameterTypes = new Class[args.length/2];
        System.arraycopy(args, 0, parameterTypes, 0, args.length/2);

        final Object[] argValues = new Object[args.length/2 + 1];
        argValues[0] = instance;
        System.arraycopy(args, args.length/2, argValues, 1, args.length/2);

        return (T) unchecked(() -> getMethodHandle(instance.getClass(), false, name, parameterTypes).invokeWithArguments(argValues));
    }
}
