package io.github.sianabeeblebrox.specter.impl;

import javax.annotation.CheckReturnValue;
import javax.annotation.ParametersAreNonnullByDefault;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static io.github.sianabeeblebrox.specter.ExceptionUtil.unchecked;
import static io.github.sianabeeblebrox.specter.ExceptionUtil.with;

/**
 * Utility methods for files and directories
 */
@ParametersAreNonnullByDefault
public final class FileUtils {
    /**
     * Lists the entries in a directory ordered lexicographically by basename descending
     * (make sure to close the returned stream)
     * @param dir the directory to list
     * @return a stream of paths within the directory
     */
    public static @CheckReturnValue Stream<Path> ls(final Path dir) {
        final Stream<Path> list = unchecked(() -> Files.list(dir));
        return list.sorted(Comparator.comparing(path -> path.getFileName().toString())).onClose(list::close);
    }

    /**
     * Deletes the given file or directory if it exists (equivalent to {@code rm -rf})
     * @param path the file or directory to delete
     */
    public static void rmrf(final Path path) {
        unchecked(() -> {
            if(Files.isDirectory(path)) {
                with(
                    () -> Files.walk(path),
                    (final Stream<Path> paths) -> paths.sorted(Comparator.reverseOrder()).forEach(p -> unchecked(() -> Files.delete(p)))
                );
            } else {
                Files.deleteIfExists(path);
            }
        });
    }

    public static void copy(final Path from, final Path to, final CopyOption... options) {
        unchecked(() -> {
            if(Files.isDirectory(from)) {
                with(
                    () -> Files.walk(from),
                    (final Stream<Path> paths) -> paths.forEach(s -> unchecked(() -> {
                        final Path d = to.resolve(from.relativize(s).toString()); // toString() needed to fix possible provider mismatch
                        if(Files.isDirectory(s)) {
                            Files.createDirectories(d);
                        } else {
                            Files.copy(s, d, options);
                        }
                    }))
                );
            } else {
                Files.copy(from, to, options);
            }
        });
    }

    /**
     * Gets the file extension without the dot from a given path if any; otherwise, returns an empty string
     * (Note: returns everything after the last dot, so "foo.tar.gz" &rarr; "gz")
     * @param path the path
     * @return the path's file extension
     */
    public static String getExtension(final Path path) {
        final String name = path.getFileName().toString();
        final int i;
        return (i = name.lastIndexOf('.')) > -1 ? name.substring(i + 1) : "";
    }

    public static String getFileName(final Path path) {
        return path.getFileName().toString();
    }
}
