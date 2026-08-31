package io.github.sianabeeblebrox.specter.impl;

import org.objectweb.asm.ClassWriter;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

@ParametersAreNonnullByDefault
public abstract class AbstractASMTransform implements ClassFileTransformer {
    /**
     * @deprecated Use overload {@link AbstractASMTransform#transform(Module, ClassLoader, String, Class, ProtectionDomain, byte[])} instead
     */
    @Deprecated
    @Override
    public final @Nullable byte[] transform(final @Nullable ClassLoader loader, final String name, final @Nullable Class<?> clazz, final ProtectionDomain domain, final byte[] bytes) throws IllegalClassFormatException {
        throw new UnsupportedOperationException();
    }

    @Override
    public abstract @Nullable byte[] transform(final Module module, final @Nullable ClassLoader loader, final String name, final @Nullable Class<?> clazz, final ProtectionDomain domain, final byte[] bytes) throws IllegalClassFormatException;

    protected final int getClassWriterFlags() {
        return  (this.shouldAutoComputeMaxs() ? ClassWriter.COMPUTE_MAXS : 0) | (this.shouldAutoComputeFrames() ? ClassWriter.COMPUTE_FRAMES : 0);
    }

    protected boolean shouldAutoComputeFrames() {
        return false;
    }

    protected boolean shouldAutoComputeMaxs() {
        return false;
    }
}
