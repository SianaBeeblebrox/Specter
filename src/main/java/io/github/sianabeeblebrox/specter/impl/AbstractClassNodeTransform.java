package io.github.sianabeeblebrox.specter.impl;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

/**
 * Abstract ASM transformer for working with class node tree API
 */
@ParametersAreNonnullByDefault
public abstract class AbstractClassNodeTransform extends AbstractASMTransform {
    @Override
    public final @Nullable byte[] transform(final Module module, final @Nullable ClassLoader loader, final String name, @Nullable final Class<?> clazz, final ProtectionDomain domain, final byte[] bytes) throws IllegalClassFormatException {
        final ClassReader reader = new ClassReader(bytes);
        final ClassWriter writer = new ClassWriter(reader, this.getClassWriterFlags());
        final ClassNode node = new ClassNode();
        reader.accept(node, 0);
        if(this.transform(module, loader, name, clazz, domain, bytes, node) instanceof final ClassNode result) {
            result.accept(writer);
            return writer.toByteArray();
        }
        return null;
    }

    /**
     * Transforms the given class node and returns a new replacement class node.
     *
     * @param loader  the defining loader of the class to be transformed,
     *                may be {@code null} if the bootstrap loader
     * @param name    the name of the class in the internal form of fully
     *                qualified class and interface names as defined in
     *                <i>The Java Virtual Machine Specification</i>.
     *                For example, <code>"java/util/List"</code>.
     * @param clazz   if this is triggered by a redefine or retransform,
     *                the class being redefined or retransformed;
     *                if this is a class load, {@code null}
     * @param domain  the protection domain of the class being defined or redefined
     * @param bytes   the input byte buffer in class file format - must not be modified
     * @param node    the class node to transform
     *
     * @return a class node (the result of the transform),
     *         or {@code null} if no transform is performed
     */
    public abstract @Nullable ClassNode transform(final Module module, @Nullable final ClassLoader loader, final String name, final @Nullable Class<?> clazz, final ProtectionDomain domain, final byte[] bytes, final ClassNode node);

    /**
     * Returns {@code true} iff all the bits set in {@code flags} are set in {@code bitmask}
     * @param bitmask the bitmask to check
     * @param flags the bits to check
     * @return {@code true} iff all the bits set in {@code flags} are set in {@code bitmask}
     */
    protected static boolean checkFlags(final int bitmask, final int flags) {
        return (bitmask & flags) == flags;
    }
}
