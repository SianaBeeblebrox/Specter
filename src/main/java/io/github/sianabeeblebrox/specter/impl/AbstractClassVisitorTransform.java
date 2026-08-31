package io.github.sianabeeblebrox.specter.impl;

import org.objectweb.asm.*;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

@ParametersAreNonnullByDefault
public abstract class AbstractClassVisitorTransform extends AbstractASMTransform {
    @Override
    public final @Nullable byte[] transform(final Module module, final @Nullable ClassLoader loader, final String name, final @Nullable Class<?> clazz, final ProtectionDomain domain, final byte[] bytes) throws IllegalClassFormatException {
        if(this.getVisitor(module, loader, name, clazz, domain) instanceof final AbstractClassVisitor visitor) {
            final ClassReader reader = new ClassReader(bytes);
            final ClassWriter writer = new ClassWriter(reader, this.getClassWriterFlags());
            visitor.setWriter(writer);
            reader.accept(visitor, 0);
            return writer.toByteArray();
        }
        return null;
    }

    public abstract static class AbstractClassVisitor extends ClassVisitor {
        public AbstractClassVisitor() {
            super(Opcodes.ASM9);
        }

        private void setWriter(final ClassWriter cw) {
            this.cv = cw;
        }
    }

    public abstract @Nullable AbstractClassVisitor getVisitor(final Module module, final @Nullable ClassLoader loader, final String name, @Nullable final Class<?> clazz, final ProtectionDomain domain);
}
