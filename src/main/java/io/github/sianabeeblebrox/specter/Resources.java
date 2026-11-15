package io.github.sianabeeblebrox.specter;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import io.github.sianabeeblebrox.specter.impl.FileUtils;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Function;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static io.github.sianabeeblebrox.specter.ExceptionUtil.*;

/**
 * Methods for tweaking resources within JAR files at runtime
 * <p><i>Note: May not work with all target programs</i></p>
 */
@ParametersAreNonnullByDefault
@SuppressWarnings("unused")
public class Resources {
    private static Path resolve(final @Nullable Class<?> clazz, final String path) {
        if(clazz == null) return Paths.get(path);
        final URI uri = nonnull(ncls(
                ignored(() -> clazz.getResource(String.format("/%s.class", clazz.getName().replace('.', '/'))).toURI()),
                unchecked(clazz.getProtectionDomain().getCodeSource().getLocation()::toURI)
        ));
        return Objects.requireNonNull(ncls(ignored(() -> Paths.get(uri)), () -> unchecked(() -> {
            FileSystems.newFileSystem(uri, Collections.emptyMap());
            return Paths.get(uri);
        }))).getParent().resolve(path).normalize();
    }

    private static Path resolveMutable(final Class<?> clazz, final String path) {
        final Path p = resolve(clazz, path);
        if(p.getFileSystem().equals(FileSystems.getDefault())) {
            return throwUnchecked(new AccessDeniedException(path));
        } else {
            return p;
        }
    }

    private static Path resolveWritable(final Class<?> clazz, final String path) {
        return unchecked(() -> {
            final Path p = resolveMutable(clazz, path);
            if(p.getParent() != null) {
                Files.createDirectories(p.getParent());
            }
            return p;
        });
    }

    /**
     * Overwrites the given resource (equivalent to {@code write(origin, path, bytes, false)})
     * @param origin class to target (within a JAR or ZIP)
     * @param path relative path to resource
     * @param bytes new bytes to write
     */
    public static void write(final Class<?> origin, final String path, final byte[] bytes) {
        write(origin, path, bytes, false);
    }

    /**
     * Overwrites the given resource (equivalent to {@code write(origin, path, text, false)})
     * @param origin class to target (within a JAR or ZIP)
     * @param path relative path to resource
     * @param text new text to write
     */
    public static void write(final Class<?> origin, final String path, final String text) {
        write(origin, path, text, false);
    }

    /**
     * Writes to the given resource
     * @param origin class to target (within a JAR or ZIP)
     * @param path relative path to resource
     * @param bytes new bytes to write
     * @param append iff true, the new bytes will be appended to the existing resource; otherwise, they will overwrite it
     */
    public static void write(final Class<?> origin, final String path, final byte[] bytes, final boolean append) {
        unchecked(() -> Files.write(resolveWritable(origin, path), bytes, append ? new OpenOption[] {StandardOpenOption.APPEND} : new OpenOption[] {}));
    }

    /**
     * Writes to the given resource
     * @param origin class to target (within a JAR or ZIP)
     * @param path relative path to resource
     * @param text new text to write
     * @param append iff true, the new text will be appended to the existing resource; otherwise, it will overwrite it
     */
    public static void write(final Class<?> origin, final String path, final String text, final boolean append) {
        write(origin, path, text.getBytes(StandardCharsets.UTF_8), append);
    }

// TODO
//    public static void clearUpdateFlag(final Class<?> origin) {
//        unchecked(() -> {
//            final FileSystem fs = getPath(origin).getFileSystem();
//            final Field hasUpdate = fs.getClass().getDeclaredField("hasUpdate");
//            hasUpdate.setAccessible(true);
//            hasUpdate.set(fs, false);
//        });
//    }

    /**
     * Reads the given resource as a byte array
     * @param origin optional class to target (within a JAR, ZIP, or directory)
     * @param path relative path to resource
     * @return the bytes comprising the resource
     */
    public static byte[] read(final @Nullable Class<?> origin, final String path) {
        return unchecked(() -> Files.readAllBytes(resolve(origin, path)));
    }

    /**
     * Reads the given resource as a string
     * @param origin optional class to target (within a JAR, ZIP, or directory)
     * @param path relative path to resource
     * @return the string text of resource
     */
    public static String readString(final @Nullable Class<?> origin, final String path) {
        return unchecked(() -> Files.readString(resolve(origin, path)));
    }

    /**
     * Recursively deletes the given resource if it exists
     * @param origin class to target (within a JAR or ZIP)
     * @param path relative path to resource
     */
    public static void delete(final Class<?> origin, final String path) {
        // Don't recursively delete on main filesystem to avoid accidents
         FileUtils.rmrf(resolveMutable(origin, path));
    }

    /**
     * Transforms the given resource using a {@link ResourceTransformer}
     * @param origin class to target (within a JAR or ZIP)
     * @param path relative path to resource
     * @param transformer the function to transform the resource
     */
    public static void transform(final Class<?> origin, final String path, final ResourceTransformer transformer) {
        copy(origin, path, origin, path, transformer);
    }

    /**
     * Copies from one given resource to another and applies a {@link ResourceTransformer} to the copied data
     * @param origin class to target (within a JAR or ZIP)
     * @param from relative path to resource to copy from
     * @param to relative path to resource to copy to
     * @param transformer the function to transform the copied resource
     */
    public static void copy(final Class<?> origin, final String from, final String to, final ResourceTransformer transformer) {
        copy(origin, from, origin, to, transformer);
    }

    /**
     * Copies from one given resource to another and applies a {@link ResourceTransformer} to the copied data
     * @param origin optional class to target (within a JAR, ZIP, or directory) to copy from
     * @param from relative path to resource to copy from
     * @param target class to target (within a JAR, or ZIP) to copy to
     * @param to relative path to resource to copy to
     * @param transformer the function to transform the copied resource
     */
    public static void copy(final @Nullable Class<?> origin, final String from, final Class<?> target, final String to, final ResourceTransformer transformer) {
        unchecked(() -> Files.write(resolveWritable(target, to), transformer.transform(Files.readAllBytes(resolve(origin, from)))));
    }

    /**
     * Recursively copies from one given resource to another
     * @param origin class to target (within a JAR or ZIP)
     * @param from relative path to resource to copy from
     * @param to relative path to resource to copy to
     */
    public static void copy(final Class<?> origin, final String from, final String to) {
        copy(origin, from, origin, to);
    }

    /**
     * Recursively copies from one given resource to another
     * @param origin optional class to target (within a JAR, ZIP, or directory) to copy from
     * @param from relative path to resource to copy from
     * @param target class to target (within a JAR or ZIP) to copy to
     * @param to relative path to resource to copy to
     */
    public static void copy(final @Nullable Class<?> origin, final String from, final Class<?> target, final String to) {
        unchecked(() -> FileUtils.copy(resolve(origin, from), resolveWritable(target, to), StandardCopyOption.REPLACE_EXISTING));
    }

    /**
     * Returns a list of entries within a directory
     * @param origin optional class to target (within a JAR, ZIP, or directory)
     * @param path relative path to directory
     */
    public static List<String> ls(final @Nullable Class<?> origin, final String path) {
        return with(() -> Files.list(resolve(origin, path)), (final Stream<Path> paths) -> paths.map(Path::toString).toList());
    }

    /**
     * A functional interface for transforming resources, also provides several standard transforms as static helpers
     */
    @FunctionalInterface
    public interface ResourceTransformer {
        byte[] transform(final byte[] bytes);

        /**
         * Creates a {@link ResourceTransformer} that applies regex substitution to all matches in a textual resource
         * @param pattern the regex pattern
         * @param replacement the replacement value
         * @return the {@link ResourceTransformer}
         */
        static ResourceTransformer replaceAll(final String pattern, final String replacement) {
            return replaceAll(Pattern.compile(pattern), r -> replacement);
        }

        /**
         * Creates a {@link ResourceTransformer} that applies regex substitution to only the first match in a textual resource
         * @param pattern the regex pattern
         * @param replacement the replacement value
         * @return the {@link ResourceTransformer}
         */
        static ResourceTransformer replaceFirst(final String pattern, final String replacement) {
            return replaceFirst(Pattern.compile(pattern), r -> replacement);
        }

        /**
         * Creates a {@link ResourceTransformer} that applies regex substitution to all matches in a textual resource
         * @param pattern the regex pattern
         * @param callback A function to map matches to replacement values
         * @return the {@link ResourceTransformer}
         */
        static ResourceTransformer replaceAll(final String pattern, final Function<MatchResult, String> callback) {
            return replaceAll(Pattern.compile(pattern), callback);
        }

        /**
         * Creates a {@link ResourceTransformer} that applies regex substitution to only the first match in a textual resource
         * @param pattern the regex pattern
         * @param callback A function to map the match to a replacement value
         * @return the {@link ResourceTransformer}
         */
        static ResourceTransformer replaceFirst(final String pattern, final Function<MatchResult, String> callback) {
            return replaceFirst(Pattern.compile(pattern), callback);
        }

        /**
         * Creates a {@link ResourceTransformer} that applies regex substitution to all matches in a textual resource
         * @param pattern the regex pattern
         * @param callback A function to map matches to replacement values
         * @return the {@link ResourceTransformer}
         */
        static ResourceTransformer replaceAll(final Pattern pattern, final Function<MatchResult, String> callback) {
            return bytes -> pattern.matcher(new String(bytes, StandardCharsets.UTF_8)).replaceAll(callback).getBytes(StandardCharsets.UTF_8);
        }

        /**
         * Creates a {@link ResourceTransformer} that applies regex substitution to only the first match in a textual resource
         * @param pattern the regex pattern
         * @param callback A function to map the match to a replacement value
         * @return the {@link ResourceTransformer}
         */
        static ResourceTransformer replaceFirst(final Pattern pattern, final Function<MatchResult, String> callback) {
            return bytes -> pattern.matcher(new String(bytes, StandardCharsets.UTF_8)).replaceFirst(callback).getBytes(StandardCharsets.UTF_8);
        }

        /**
         * Creates a {@link ResourceTransformer} that modifies a JSON resource via a mapping function
         * @param transform a function to transform the JSON resource (can deeply modify input, must return new value)
         * @return the {@code ResourceTransformer}
         */
        static ResourceTransformer json(final Function<Object, Object> transform) {
            return bytes -> JsonOutput.toJson(transform.apply(new JsonSlurper().parse(bytes))).getBytes(StandardCharsets.UTF_8);
        }

        /**
         * Creates a {@link ResourceTransformer} that modifies an image resource via a mapping function
         * @param transform a function to transform the image resource (must return new value)
         * @return the {@code ResourceTransformer}
         */
        static ResourceTransformer image(final Function<BufferedImage, BufferedImage> transform) {
            return bytes -> unchecked(() -> {
                final String format = StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(
                            ImageIO.getImageReaders(ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))),
                            Spliterator.ORDERED
                        ), false
                    ).map(f -> unchecked(f::getFormatName).toLowerCase(Locale.ROOT))
                    .findFirst()
                    .get()
                ;

                final ByteArrayOutputStream stream = new ByteArrayOutputStream();
                ImageIO.write(transform.apply(ImageIO.read(new ByteArrayInputStream(bytes))), format, stream);
                return stream.toByteArray();
            });
        }
    }
}
