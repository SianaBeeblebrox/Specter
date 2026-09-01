package io.github.sianabeeblebrox.specter.annotations.impl;

import io.github.sianabeeblebrox.specter.annotations.Static;
import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.classtransform.transformer.types.RemovingAnnotationHandler;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

public final class StaticTransformation extends RemovingAnnotationHandler<Static> {
    public StaticTransformation() {
        super(Static.class);
    }

    @Override
    public void transform(final Static annotation, final TransformerManager tm, final ClassNode transformedClass, final ClassNode transformerClass, final MethodNode transformerMethod) {
        transformerMethod.access |= Opcodes.ACC_STATIC;
    }
}
