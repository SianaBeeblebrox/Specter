package io.github.sianabeeblebrox.specter;

import groovy.lang.GroovySystem;
import groovy.lang.MetaMethod;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.macro.runtime.Macro;
import org.codehaus.groovy.macro.runtime.MacroContext;
import org.codehaus.groovy.reflection.CachedClass;
import org.codehaus.groovy.reflection.ClassInfo;
import org.codehaus.groovy.runtime.m12n.ExtensionModuleScanner;
import org.codehaus.groovy.runtime.m12n.MetaInfExtensionModule;
import org.codehaus.groovy.runtime.memoize.EvictableCache;
import org.codehaus.groovy.runtime.metaclass.MetaClassRegistryImpl;
import org.codehaus.groovy.transform.stc.AbstractExtensionMethodCache;

import java.util.*;

import static io.github.sianabeeblebrox.specter.ExceptionUtil.*;

/**
 * Compile-time macros
 */
public final class Macros {
    static {
        addExtensionModule(Specter.class.getSimpleName(), Specter.VERSION.toString(), List.of(Macros.class), List.of());
    }

    private static void addExtensionModule(final String name, final String version, final List<Class<?>> instanceExtensionClasses, final List<Class<?>> staticExtensionClasses) {
        unchecked(() -> {
            final var module = Dynamics.<MetaInfExtensionModule>invoke(
                    MetaInfExtensionModule.class,
                    "<init>",
                    String.class, String.class, List.class, List.class,
                    name, version, instanceExtensionClasses, staticExtensionClasses
            );

            final var registry = ((MetaClassRegistryImpl) GroovySystem.getMetaClassRegistry());
            final var metaMethods = new HashMap<CachedClass, List<MetaMethod>>();

            Dynamics.<ExtensionModuleScanner.ExtensionModuleListener>invoke(
                    Arrays.stream(registry.getClass().getDeclaredClasses()).filter(clazz -> "DefaultModuleListener".equals(clazz.getSimpleName())).findFirst().get(),
                    "<init>",
                    registry.getClass(), Map.class,
                    registry, metaMethods
            ).onModule(module);

            metaMethods.forEach((clazz, methods) -> {
                clazz.addNewMopMethods(methods);
                ClassInfo.getAllClassInfo().forEach(info -> {
                    if(clazz.getTheClass().isAssignableFrom(info.getCachedClass().getTheClass())) {
                        info.getCachedClass().addNewMopMethods(methods);
                    }
                });
            });

            final var mmc = Dynamics.<AbstractExtensionMethodCache>get(Class.forName("org.codehaus.groovy.macro.transform.MacroMethodsCache"), "INSTANCE");
            final var cache = Dynamics.<EvictableCache<ClassLoader, Map<String, List<MethodNode>>>>get(mmc, "cache");
            final var macroMethods = new HashMap<>(nonnull(ncls(cache.get(Specter.getClassLoader()), Collections.emptyMap())));

            macroMethods.putAll(Dynamics.<Map<String, List<MethodNode>>>invoke(mmc, "makeMethodsUnmodifiable", Map.class, Dynamics.<Map<String, List<MethodNode>>>invoke(mmc, "getMethods", List.class, List.of(module))));
            cache.put(Specter.getClassLoader(), Collections.unmodifiableMap(macroMethods));
        });
    }

    /**
     * Runtime stub for {@code strc(...)} calls.
     * @param self the receiver
     * @param arg the macro argument
     * @return never returns normally
     */
    public static String strc(final Object self, final Object arg) {
        throw new IllegalStateException("Macros.strc(Object) should never be called at runtime. Are you sure you are using it correctly?");
    }

    /**
     * Returns the underlying string source for an expression
     * @param ctx the current macro context
     * @param arg the expression
     * @return the string source
     */
    @Macro
    public static ConstantExpression strc(final MacroContext ctx, final Expression arg) {
        final var sb = new StringBuilder();
        for(int lineno = arg.getLineNumber(); lineno <= arg.getLastLineNumber(); lineno++) {
            final String line = ctx.getSourceUnit().getSource().getLine(lineno, null);
            sb.append(line, lineno == arg.getLineNumber() ? arg.getColumnNumber() - 1 : 0, lineno == arg.getLastLineNumber() ? arg.getLastColumnNumber() - 1 : line.length());
            if(lineno != arg.getLastLineNumber()) sb.append("\n");
        }
        return new ConstantExpression(sb.toString());
    }
}
