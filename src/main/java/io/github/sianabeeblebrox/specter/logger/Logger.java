package io.github.sianabeeblebrox.specter.logger;

import io.github.sianabeeblebrox.specter.Specter;

import javax.annotation.Nullable;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.github.sianabeeblebrox.specter.ExceptionUtil.ignored;
import static io.github.sianabeeblebrox.specter.ExceptionUtil.unchecked;

/**
 * An abstract logger
 */
public abstract class Logger {
    private final String name;
    private final ExecutorService executor;

    protected Logger(final String name) {
        this.name = name;
        this.executor = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    }

    /**
     * Returns the logger's name
     * @return the logger's name
     */
    public final String getName() {
        return name;
    }

    /**
     * Asynchronously logs a message at the given level if the implementation supports it
     * @param level the level to log at
     * @param args the message arguments (Stringified with {@link Logger#stringify(Object...)})
     */
    public final void log(final String level, final Object... args) {
        final String parent = Thread.currentThread().getName();
        this.executor.submit(() -> {
            Thread.currentThread().setName(this.getName() + "-" + parent);
            this.log(level.toLowerCase(Locale.ROOT), stringify(args));
        });
    }

    protected abstract void log(final String level, final String message);

    /**
     * Pretty-prints objects to a string (Has special support for {@code null}, {@link Spliterator}, {@link Iterator},
     * {@link Stream}, {@link Throwable}, and arrays (note, may consume value); other types use {@link Object#toString()})
     * @param args the objects to stringify
     * @return stringified arguments
     */
    public static String stringify(final Object... args) {
        return Arrays.stream(args).map(arg -> switch(arg) {
            case null -> "null";
            case final Spliterator<?> spliterator -> StreamSupport.stream(spliterator, false).collect(Collectors.toList());
            case final Iterator<?> iterator -> StreamSupport.stream(Spliterators.spliteratorUnknownSize(iterator, 0), false).collect(Collectors.toList());
            case final Stream<?> stream -> stream.collect(Collectors.toList());
            case final Object array when array.getClass().isArray() -> {
                final List<Object> list = new ArrayList<>(Collections.nCopies(Array.getLength(array), null));
                for(int i = 0; i < list.size(); i++) list.set(i, Array.get(array, i));
                yield list;
            }
            case final Throwable throwable -> {
                final StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                yield sw.toString();
            }
            default -> arg;
        }).map(String::valueOf).collect(Collectors.joining(""));
    }

    /**
     * An abstract logger that can be backed by popular libraries like
     * <a href="https://logging.apache.org/log4j/2.x/javadoc/log4j-api/org/apache/logging/log4j/Logger.html">Log4j</a>
     * or <a href="https://www.slf4j.org/apidocs/org/slf4j/Logger.html">SLF4J</a> via reflection
     */
    public static final class ExternalLogger extends Logger {
        private final Object implementation;

        private final ConcurrentHashMap<String, Method> cache = new ConcurrentHashMap<>();

        private ExternalLogger(final String name, final Object implementation) {
            super(name);
            this.implementation = implementation;
        }

        @Override
        public void log(final String level, final String message) {
            if(this.cache.computeIfAbsent(level, name -> ignored(() -> this.implementation.getClass().getMethod(name.toLowerCase(Locale.ROOT), String.class))) instanceof final Method method) {
                unchecked(() -> method.invoke(this.implementation, message));
            }
        }
    }

    /**
     * Simple logger implementation that prints to STDOUT
     */
    public static final class DefaultLogger extends Logger {
        public static String FORMAT = "[%1$tH:%1$tM:%1$tS] [%2$s/%3$s]: %4$s";

        private DefaultLogger(final String name) {
           super(name);
        }

        @Override
        public void log(final String level, final String message) {
            System.out.printf(
                    this.getFormatString(),
                    ZonedDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()),
                    Thread.currentThread().getName(),
                    level.toUpperCase(Locale.ROOT),
                    message,
                    switch(level) {
                        case "error" -> "\u001B[31m";
                        case "warn" -> "\u001B[33m";
                        default -> "";
                    }
            );
        }

        private String getFormatString() {
            return (System.console() != null ? "%5$s" + FORMAT + "\u001B[0m" : FORMAT) + "%n";
        }
    }

    /**
     * Creates a {@link DefaultLogger} with a given name
     * @param name the logger's name
     * @return the new logger
     */
    public static Logger getLogger(final String name) {
        return new DefaultLogger(name);
    }

    /**
     * Creates a {@link DefaultLogger} named after the calling class
     * @return the new logger
     */
    public static Logger getLogger() {
        return Logger.getLogger(Specter.getCallerClass().getSimpleName());
    }

    /**
     * Creates an {@link ExternalLogger} with a given name using a given implementation
     * @param provider the name of a class containing a {@code public static Object getLogger(String name)} method to
     *                 create a logger with
     * @param name the logger's name
     * @return the new logger if successful otherwise {@code null}
     */
    public static @Nullable ExternalLogger getExternalLogger(final String provider, final String name) {
        return ignored(() -> new ExternalLogger(name, Class.forName(provider.replace('/', '.')).getMethod("getLogger", String.class).invoke(null, name)));
    }

    /**
     * Creates an {@link ExternalLogger} named after the calling class using a given implementation
     * @param provider the name of a class containing a {@code public static Object getLogger(String name)} method to
     *                 create a logger with
     * @return the new logger if successful otherwise {@code null}
     */
    public static @Nullable ExternalLogger getExternalLogger(final String provider) {
        return Logger.getExternalLogger(provider, Specter.getCallerClass().getSimpleName());
    }

}
