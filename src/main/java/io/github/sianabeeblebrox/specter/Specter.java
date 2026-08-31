package io.github.sianabeeblebrox.specter;

import groovy.lang.*;
import io.github.sianabeeblebrox.specter.annotations.Transformer;
import io.github.sianabeeblebrox.specter.annotations.impl.AnnotationPreprocessor;
import io.github.sianabeeblebrox.specter.impl.AbstractClassNodeTransform;
import io.github.sianabeeblebrox.specter.impl.GroovyByteClassLoader;
import io.github.sianabeeblebrox.specter.logger.Logger;
import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.classtransform.utils.tree.BasicClassProvider;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import javax.annotation.Nullable;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static io.github.sianabeeblebrox.specter.ExceptionUtil.*;
import static io.github.sianabeeblebrox.specter.Dynamics.get;
import static io.github.sianabeeblebrox.specter.Dynamics.invoke;

public final class Specter {
    public static final String VERSION = Specter.class.getPackage().getImplementationVersion();
    private static final ConcurrentHashMap<Class<?>, String[]> ARGS = new ConcurrentHashMap<>();
    private static final GroovyClassLoader CLASS_LOADER = new GroovyByteClassLoader(Specter.class.getClassLoader()) {
        @Override
        public Class<?> onClassDefined(final Class<?> clazz) {
            addTransformers(clazz);
            return super.onClassDefined(clazz);
        }

        private void addTransformers(final Class<?> clazz) {
            if(clazz.isAnnotationPresent(Transformer.class)) {
                LOGGER.log("info", "Adding transformer ", clazz.getName());
                TRANSFORMER_MANAGER.addTransformer(clazz.getName());
            }
            for(final Class<?> child : clazz.getDeclaredClasses()) {
                addTransformers(child);
            }
        }
    };
    private static final TransformerManager TRANSFORMER_MANAGER = new TransformerManager(new BasicClassProvider(CLASS_LOADER));
    private static Instrumentation INSTRUMENTATION;
    static Logger LOGGER = ncls(
            Logger.getExternalLogger("org.apache.logging.log4j.LogManager"),
            () -> Logger.getExternalLogger("org.slf4j.LoggerFactory"),
            Logger::getLogger
    );
    public static final EventBus EVENT_BUS = new EventBus();

    public static void premain(final String args, final Instrumentation instrumentation) {
        Specter.INSTRUMENTATION = instrumentation;

        LOGGER.log("info", "Definitely up to no good");

        Specter.openModules(instrumentation);
        instrumentation.addTransformer(new AccessTweaker(), true);
        instrumentation.addTransformer(new AbstractClassNodeTransform() {
            private final Method CALLBACK = unchecked(() -> Specter.class.getDeclaredMethod("_main", String[].class));
            @Override
            public ClassNode transform(final Module module, final ClassLoader loader, final String name, final Class<?> clazz, final ProtectionDomain domain, final byte[] bytes, final ClassNode node) {
                for(final MethodNode method : node.methods) {
                    if(checkFlags(method.access, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC) && method.name.equals("main") && method.desc.equals("([Ljava/lang/String;)V")) {
                        final InsnList instructions = new InsnList();
                        instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                        instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Type.getInternalName(CALLBACK.getDeclaringClass()), CALLBACK.getName(), Type.getMethodDescriptor(CALLBACK), false));
                        method.instructions.insert(instructions);
                        return node;
                    }
                }
                return null;
            }
        }, true);

        TRANSFORMER_MANAGER.addTransformerPreprocessor(new AnnotationPreprocessor());

        if(args != null && !args.isBlank()) {
            List<Path> paths = new ArrayList<>();

            ls(Path.of(args), path -> {
//            println(path);
                if(Files.isRegularFile(path)) {
                    switch(getExtension(path)) {
                        case "jar" -> {
                            LOGGER.log("info", "Found jar ", path);
                            addURL(unchecked(() -> path.toUri().toURL()));
                        }
                        case "groovy" -> unchecked(() -> {
//                        final Class<?> clazz = CLASS_LOADER.parseClass(new GroovyCodeSource(path.toUri()));
//                        if(Script.class.isAssignableFrom(clazz)) {
//                            InvokerHelper.newScript((Class<? extends Script>) clazz, new Binding()).run();
//                        }
                            paths.add(path);
                        });
                    }
                }
            }, true);

            for(final Path path : paths) {
                unchecked(() -> {
                    final Class<?> clazz = CLASS_LOADER.parseClass(new GroovyCodeSource(path.toUri()));
                    if(Script.class.isAssignableFrom(clazz)) {
                        InvokerHelper.newScript((Class<? extends Script>) clazz, new Binding()).run();
                    }
                });
            }
        }

        TRANSFORMER_MANAGER.hookInstrumentation(instrumentation);
        Specter.EVENT_BUS.dispatch("premain", instrumentation);
    }

    private static void ls(final Path root, final Consumer<Path> callback, final boolean unzip) {
        try(final var stream = Files.list(root)) {
            stream.forEach(path -> {
                callback.accept(path);
                if(Files.isDirectory(path)) {
                    ls(path, callback, false);
                } else if(unzip && "zip".equals(getExtension(path))) {
                    try(final FileSystem zip = FileSystems.newFileSystem(path)) {
                        zip.getRootDirectories().forEach(rd -> ls(rd, callback, false));
                    } catch(final Throwable t) {
                        throwUnchecked(t);
                    }
                }
            });
        } catch(final Throwable t) {
            throwUnchecked(t);
        }
    }

    private static String getExtension(final Path path) {
        final String name = path.getFileName().toString();
        final int i;
        return (i = name.indexOf('.')) > -1 ? name.substring(i + 1) : "";
    }

    // TODO download zip
    private static Path download(final URL url) {
        return null;
    }

    private static void openModules(final Instrumentation instrumentation) {
        // Automatic modules are exported and opened by default while explicit modules are not,
        // so add all the automatic ones to the explicit ones exports and opens
        // https://stackoverflow.com/a/46742802
        final Set<Module>
                automatic = new HashSet<>(Set.of(Specter.class.getClassLoader().getUnnamedModule())),
                explicit = new HashSet<>()
        ;

        ModuleLayer.boot().modules().forEach(module -> (module.getDescriptor().isAutomatic() ? automatic : explicit).add(module));

        for(final Module module : explicit) {
            final Map<String, Set<Module>> extra = Map.ofEntries(module.getPackages().stream().map(pkg -> Map.entry(pkg, automatic)).toArray(Map.Entry[]::new));
            instrumentation.redefineModule(module, Collections.emptySet(), extra, extra, Collections.emptySet(), Collections.emptyMap());
        }
    }

    /**
     * Returns the class this method was called from (avoid using within lambdas or closures!)
     * @return the class this method was called from
     */
    public static @Nullable Class<?> getCallerClass() {
        return ignored(() -> StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream -> stream.skip(5).limit(1).collect(Collectors.toList())).getFirst().getDeclaringClass());
    }

    /**
     * Returns the string name of the method this method was called fromm (avoid using within lambdas or closures!)
     * @return the string name of the method this method was called from
     */
    public static @Nullable String getCallerMethodName() {
        return ignored(() -> StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(stream -> stream.skip(5).limit(1).collect(Collectors.toList())).getFirst().getMethodName());
    }

    /**
     * Adds a URL to the class path
     * @param url the url to add
     */
    public static void addURL(final URL url) {
        invoke((Object) get(ClassLoader.getSystemClassLoader(), "ucp"), "addURL", URL.class, url);
    }

    /**
     * @deprecated Injected callback, do not call manually!
     */
    @Deprecated
    public static void _main(final String[] args) {
        final Class<?> caller = getCallerClass();
        if(Specter.ARGS.putIfAbsent(caller, args) == null) {
            LOGGER.log("info", "Entering main method in ", caller.getName());
            Specter.EVENT_BUS.dispatch("main", caller, args);
        }
    }

    /**
     * Gets a copy of the arguments passed to the given class's {@code public static void main(String[] args)} method or
     * {@code null} if no such method exists or has been called yet
     * @param clazz the class of interest
     * @return a copy of the arguments
     */
    public static @Nullable String[] getArgs(final Class<?> clazz) {
        return Optional.ofNullable(ARGS.get(clazz)).map(String[]::clone).orElse(null);
    }

    // do u seriously need documentation for getters??
    public static GroovyClassLoader getClassLoader() {
        return CLASS_LOADER;
    }
    public static Instrumentation getInstrumentation() {
        return INSTRUMENTATION;
    }
    public static TransformerManager getTransformManager() {
        return TRANSFORMER_MANAGER;
    }
    public static Logger getLogger() {
        return Specter.LOGGER;
    }
    public static void setLogger(final Logger logger) {
        Specter.LOGGER = logger;
    }
}
