package io.github.sianabeeblebrox.specter;

import io.github.sianabeeblebrox.specter.impl.AbstractClassVisitorTransform;
import org.objectweb.asm.*;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.sianabeeblebrox.specter.ExceptionUtil.*;

@ParametersAreNonnullByDefault
public final class AccessTweaker extends AbstractClassVisitorTransform {
    private static final Pattern AT_PATTERN = Pattern.compile("(?U)^(?<modifiers>(?:[+-][a-zA-Z]+\\s+)*)(?<class>\\p{javaJavaIdentifierStart}[\\p{javaJavaIdentifierPart}./]*)(?:\\s+(?<member>\\p{javaUnicodeIdentifierStart}\\p{javaJavaIdentifierPart}*|\\*)(?:(?:\\s+|(?=\\())(?<descriptor>.*))?)?$");
    private static final ConcurrentHashMap<String /*class*/, ConcurrentHashMap<String /*member*/, ConcurrentHashMap<String /*descriptor*/, AccessTweakerFlags>>> AT_DATA = new ConcurrentHashMap<>();

    AccessTweaker() {}

    /**
     * Thrown on access tweaker format error
     */
    public static final class AccessTweakerFormatException extends RuntimeException {
        private AccessTweakerFormatException(final String message) {
            super(message);
        }
    }

    private record AccessTweakerFlags(int add, int remove) {
        public AccessTweakerFlags() {
            this(0, 0);
        }

        public AccessTweakerFlags append(final int add, final int remove) {
            return new AccessTweakerFlags(this.add | add, this.remove | remove);
        }
    }

    private static int collectModifiers(final String prefix, final String[] modifiers) {
        return Arrays.stream(modifiers).filter(modifier -> modifier.startsWith(prefix)).mapToInt(modifier -> switch(modifier.substring(prefix.length())) {
            case "public" -> Opcodes.ACC_PUBLIC;
            case "private" -> Opcodes.ACC_PRIVATE;
            case "protected" -> Opcodes.ACC_PROTECTED;
            case "final" -> Opcodes.ACC_FINAL;
            default -> throw new AccessTweakerFormatException(String.format("Unknown access modifier '%s'", modifier.substring(prefix.length())));
        }).reduce((a, c) -> a | c).orElse(0);
    }

    /**
     * Modifies the access flags on a class or its members
     *
     * <h1>Access Tweaker Format</h1>
     * {@code <modifiers...> <class> [member] [descriptor]}
     * <ul>
     *     <li>
     *         {@code <modifiers...>} is one or more of {@code public}, {@code protected}, {@code private}, or
     *         {@code final} prefixed by {@code +} to add the modifier or {@code -} to remove it separated by spaces
     *     </li>
     *     <li>
     *         {@code <class>} is the fully qualified package and name of the class to target (Use {@code .} or
     *         {@code /} for packages and {@code $} for inner classes)
     *     </li>
     *     <li>
     *         If present, {@code [member]} the name of the member within the class to target
     *     </li>
     *     <li>
     *         If present, {@code [descriptor]} further limits which member (among fields, methods, and overloads of the
     *         same name) of the class to target (May contain whitespace, if a descriptor is given, a member name must
     *         also be given)
     *     </li>
     * </ul>
     *
     * <h1>Examples</h1>
     * <ul>
     *     <li>{@code +public -private -final com.example.Main main([Ljava/lang/String;) V}</li>
     *     <li>{@code +public com.example.Foo$Bar}</li>
     *     <li>{@code +public -protected -final com.example.User name Ljava/lang/String;}</li>
     * </ul>
     *
     * Multiple access tweakers are allowed to target the same class and/or member. All collective modifiers to remove
     * are applied before all collective modifiers to add. Throws on format error or if targeted class is already loaded
     * and cannot be re-transformed.
     *
     * @param arg the access tweaker string
     */
    public static void access(final String arg) {
        if(AT_PATTERN.matcher(arg.strip()) instanceof final Matcher matcher && matcher.find()) {
            final String[] modifiers = matcher.group("modifiers").strip().toLowerCase(Locale.ROOT).split("\\s+");
            AT_DATA.computeIfAbsent(matcher.group("class").replace('.', '/'), k -> new ConcurrentHashMap<>())
               .computeIfAbsent(ncls(matcher.group("member"), ""), k -> new ConcurrentHashMap<>())
               .compute(nonnull(ncls(matcher.group("descriptor"), "")).replace('.', '/').replaceAll("\\s+", ""), (final String descriptor, final @Nullable AccessTweakerFlags entry) -> nonnull(ncls(entry, AccessTweakerFlags::new)).append(
                       collectModifiers("+", modifiers),
                       collectModifiers("-", modifiers)
               ));

            if(Dynamics.invoke(ClassLoader.getSystemClassLoader(), "findLoadedClass", String.class, matcher.group("class").replace('/', '.')) instanceof Class<?> clazz) {
                unchecked(() -> Specter.getInstrumentation().retransformClasses(clazz));
            }
        } else {
            throw new AccessTweakerFormatException(String.format("Bad access tweaker format '%s'", arg));
        }
    }

    /**
     * Gets the tweaked ASM access flags for the given class or member
     * @param access the current flags
     * @param clazz the class name
     * @param member the member name or {@code null} if targeting the class
     * @param descriptor the member descriptor or {@code null} if targeting the class
     * @return the new access flags
     */
    private static int getAccess(final int access, final String clazz, final @Nullable String member, final @Nullable String descriptor) {
        return Optional.ofNullable(AT_DATA.get(clazz.replace('.', '/')))
                .flatMap(m -> Optional.ofNullable(m.get(ncls(member, ""))))
                .flatMap(m -> Optional.ofNullable(ncls(m.get(ncls(descriptor, "")), () -> m.get(""))))
                .map(at -> (access & ~at.remove) | at.add)
                .orElse(access);
    }

    @Override
    public @Nullable AbstractClassVisitor getVisitor(final Module module, final @Nullable ClassLoader loader, final String name, final @Nullable Class<?> clazz, final ProtectionDomain domain) {
        return new AbstractClassVisitor() {
            @Override
            public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
                super.visit(version, getAccess(access, name, null, null), name, signature, superName, interfaces);
            }

            @Override
            public FieldVisitor visitField(final int access, final String field, final String descriptor, final String signature, final Object value) {
                return super.visitField(getAccess(access, name, field, descriptor), field, descriptor, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(final int access, final String method, final String descriptor, final String signature, final String[] exceptions) {
                return super.visitMethod(getAccess(access, name, method, descriptor), method, descriptor, signature, exceptions);
            }
        };
    }
}
