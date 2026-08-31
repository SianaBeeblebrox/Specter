package io.github.sianabeeblebrox.specter.annotations.impl;

import io.github.sianabeeblebrox.specter.annotations.*;
import net.lenni0451.classtransform.annotations.*;
import net.lenni0451.classtransform.annotations.injection.*;
import net.lenni0451.classtransform.transformer.IAnnotationHandlerPreprocessor;
import net.lenni0451.classtransform.utils.annotations.AnnotationUtils;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ParametersAreNonnullByDefault
public final class AnnotationPreprocessor implements IAnnotationHandlerPreprocessor {
    @FunctionalInterface
    @ParametersAreNonnullByDefault
    public interface AnnotationTranslator {
        void translate(final AnnotationNode annotation, final Map<String, Object> values);
    }

    private static final Map<Type, AnnotationTranslator> ANNOTATION_TRANSLATORS = new HashMap<>();

    static {
        registerTranslator(ASM.class, CASM.class, Map.of("shift", CASM.Shift.class));
        registerTranslator(Inject.class, CInject.class);
        registerTranslator(Inline.class, CInline.class);
        registerTranslator(Local.class, CLocalVariable.class);
        registerTranslator(ModifyConstant.class, CModifyConstant.class);
        registerTranslator(ModifyExpressionValue.class, CModifyExpressionValue.class);
        registerTranslator(Overwrite.class, COverride.class);
        registerTranslator(RecordComponent.class, CRecordComponent.class);
        registerTranslator(Redirect.class, CRedirect.class);
        registerTranslator(ReplaceCallback.class, CReplaceCallback.class);
        registerTranslator(Shadow.class, CShadow.class);
        registerTranslator(Shared.class, CShared.class);
        registerTranslator(Slice.class, CSlice.class);
        registerTranslator(Stub.class, CStub.class);
        registerTranslator(Stub.class, CStub.class, Map.of("access", CStub.Access.class));
        registerTranslator(Target.class, CTarget.class, Map.of("shift", CTarget.Shift.class));
        registerTranslator(Transformer.class, CTransformer.class);
        registerTranslator(Upgrade.class, CUpgrade.class);
        registerTranslator(WrapCatch.class, CWrapCatch.class);
        registerTranslator(WrapCondition.class, CWrapCondition.class);
    }

    private static void registerTranslator(final Class<?> from, final Class<?> to) {
        registerTranslator(from, to, Map.of());
    }

    @SuppressWarnings("unchecked")
    private static void registerTranslator(final Class<?> from, final Class<?> to, final Map<String, Class<? extends Enum>> enumsFieldTypes) {
        registerTranslator(Type.getType(from), (annotation, values) -> {
            annotation.desc = Type.getType(to).getDescriptor();
            for(final var value : values.entrySet()) {
                if(value.getValue() instanceof final List<?> list && !list.isEmpty() && list.getFirst() instanceof AnnotationNode) {
                    translate((List<AnnotationNode>) list);
                } else if(value.getValue() instanceof final String[] array && enumsFieldTypes.get(value.getKey()) instanceof final Class<?> type) {
                    array[0] = Type.getType(type).getDescriptor();
                }
            }
        });
    }

    private static void registerTranslator(final Type type, final AnnotationTranslator translator) {
        ANNOTATION_TRANSLATORS.put(type, translator);
    }

    private static AnnotationTranslator getTranslator(final Type type) {
        return ANNOTATION_TRANSLATORS.get(type);
    }

    @Override
    public void process(final ClassNode node) {
        translate(node.visibleAnnotations);
        translate(node.invisibleAnnotations);
        for (final FieldNode field : node.fields) {
            translate(field.visibleAnnotations);
            translate(field.invisibleAnnotations);
        }
        for (final MethodNode method : node.methods) {
            translate(method.visibleAnnotations);
            translate(method.invisibleAnnotations);
            translate(method.visibleParameterAnnotations);
            translate(method.invisibleParameterAnnotations);
        }
    }

    private static void translate(@Nullable final List<AnnotationNode>[] annotations) {
        if (annotations == null) return;
        for (final List<AnnotationNode> annotationList : annotations) translate(annotationList);
    }

    private static void translate(@Nullable final List<AnnotationNode> annotations) {
        if (annotations == null) return;
        for (final AnnotationNode annotation : annotations) {
            final AnnotationTranslator translator = AnnotationPreprocessor.getTranslator(Type.getType(annotation.desc));
            if (translator != null) {
                Map<String, Object> values = AnnotationUtils.listToMap(annotation.values);
                translator.translate(annotation, values);
                annotation.values = AnnotationUtils.mapToList(values);
            }
        }
    }
}
