package io.github.sianabeeblebrox.specter;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;

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
import java.util.stream.StreamSupport;

import static io.github.sianabeeblebrox.specter.ExceptionUtil.*;

/**
 * Methods for tweaking resources within JAR files at runtime
 * <p><i>Note: May not work with all target programs</i></p>
 */
@ParametersAreNonnullByDefault
public class Resources {
    private static Path getPath(final URI uri) {
        return Objects.requireNonNull(ncls(ignored(() -> Paths.get(uri)), () -> unchecked(() -> {
            FileSystems.newFileSystem(uri, Collections.emptyMap());
            return Paths.get(uri);
        })));
    }

    private static Path getPath(final Class<?> origin) {
        return unchecked(() -> getPath(origin.getResource("/" + origin.getName().replace('.', '/') + ".class").toURI()));
    }

    private static Path getPath(final Class<?> origin, final String path) {
        return getPath(origin).resolve("/").resolve(path);
    }

    /**
     * Overwrites the given resource (equivalent to {@code write(origin, path, bytes, false)})
     * @param origin any class within the target JAR
     * @param path absolute path to resource within JAR
     * @param bytes new bytes to write
     */
    public static void write(final Class<?> origin, final String path, final byte[] bytes) {
        write(origin, path, bytes, false);
    }

    /**
     * Overwrites the given resource (equivalent to {@code write(origin, path, text, false)})
     * @param origin any class within the target JAR
     * @param path absolute path to resource within JAR
     * @param text new text to write
     */
    public static void write(final Class<?> origin, final String path, final String text) {
        write(origin, path, text, false);
    }

    /**
     * Writes to the given resource
     * @param origin any class within the target JAR
     * @param path absolute path to resource within JAR
     * @param bytes new bytes to write
     * @param append iff true, the new bytes will be appended to the existing resource; otherwise, they will overwrite it
     */
    public static void write(final Class<?> origin, final String path, final byte[] bytes, final boolean append) {
        unchecked(() -> {
            final Path p = getPath(origin, path);
            Files.createDirectories(p.getParent());
            Files.write(p, bytes, append ? new OpenOption[] {StandardOpenOption.APPEND} : new OpenOption[] {});
        });
    }

    /**
     * Writes to the given resource
     * @param origin any class within the target JAR
     * @param path absolute path to resource within JAR
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
     * @param origin any class within the target JAR
     * @param path absolute path to resource within JAR
     * @return the bytes comprising the resource
     */
    public static byte[] read(final Class<?> origin, final String path) {
        return unchecked(() -> Files.readAllBytes(getPath(origin, path)));
    }

    /**
     * Deletes the given resource
     * @param origin any class within the target JAR
     * @param path absolute path to resource within JAR
     */
    public static void delete(final Class<?> origin, final String path) {
        unchecked(() -> Files.delete(getPath(origin, path)));
    }

    /**
     * Transforms the given resource using a {@link ResourceTransformer}
     * @param origin any class within the target JAR
     * @param path absolute path to resource within JAR
     * @param transformer the function to transform the resource
     */
    public static void transform(final Class<?> origin, final String path, final ResourceTransformer transformer) {
        unchecked(() -> {
            final Path p = getPath(origin, path);
            Files.write(p, transformer.transform(Files.readAllBytes(p)));
        });
    }

    /**
     * Copies from one given resource to another and applies a {@link ResourceTransformer} to the copied data
     * @param origin any class within the target JAR
     * @param from absolute path to resource within JAR to copy from
     * @param to absolute path to resource within JAR to copy to
     * @param transformer the function to transform the copied resource
     */
    public static void copy(final Class<?> origin, final String from, final String to, final ResourceTransformer transformer) {
        unchecked(() -> Files.write(getPath(origin, to), transformer.transform(Files.readAllBytes(getPath(origin, from)))));
    }

    /**
     * A functional interface for transforming resources, also provides several standard transforms as static helpers
     */
    @FunctionalInterface
    public interface ResourceTransformer {
        byte[] transform(final byte[] bytes);

        /**
         * Creates a no-op {@link ResourceTransformer} that returns the input unchanged
         * @return the {@link ResourceTransformer}
         */
        static ResourceTransformer noop() {
            return bytes -> bytes;
        }

        /**
         * Creates a {@link ResourceTransformer} that applies regex substitution to all matches in a textual resource
         * @param pattern the regex pattern
         * @param callback A function to map matches to replacement values
         * @return the {@link ResourceTransformer}
         */
        static ResourceTransformer replaceAll(final Pattern pattern, Function<MatchResult, String> callback) {
            return bytes -> pattern.matcher(new String(bytes, StandardCharsets.UTF_8)).replaceAll(callback).getBytes(StandardCharsets.UTF_8);
        }

        /**
         * Creates a {@link ResourceTransformer} that applies regex substitution to only the first match in a textual resource
         * @param pattern the regex pattern
         * @param callback A function to map the match to a replacement value
         * @return the {@link ResourceTransformer}
         */
        static ResourceTransformer replaceFirst(final Pattern pattern, Function<MatchResult, String> callback) {
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
