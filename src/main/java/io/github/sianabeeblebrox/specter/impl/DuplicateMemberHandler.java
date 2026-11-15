package io.github.sianabeeblebrox.specter.impl;

import net.lenni0451.classtransform.TransformerManager;
import net.lenni0451.classtransform.transformer.AnnotationHandler;
import org.objectweb.asm.tree.ClassNode;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/**
 * Transformers written in Groovy add some synthetic members needed for
 * the language to work; this removes duplicates if multiple transformers
 * target the same class so that the transformation doesn't fail. The last
 * duplicate is kept.
 */
@ParametersAreNonnullByDefault
public final class DuplicateMemberHandler extends AnnotationHandler {
    private static final Set<String> FIELDS = Set.of(
            "$staticClassInfo Lorg/codehaus/groovy/reflection/ClassInfo;",
            "__$stMC Z",
            "metaClass Lgroovy/lang/MetaClass;"
    );
    private static final Set<String> METHODS = Set.of(
            "getMetaClass()Lgroovy/lang/MetaClass;",
            "setMetaClass(Lgroovy/lang/MetaClass;)V"
    );

    private static <T,K> void removeMatches(final List<T> a, final List<T> b, final Function<T, K> f, final Set<K> targets) {
        a.removeIf(t -> {
            final K k = f.apply(t);
            return targets.contains(k) && b.stream().anyMatch(other -> Objects.equals(k, f.apply(other)));
        });
    }

    @Override
    public void transform(final TransformerManager transformerManager, final ClassNode target, final ClassNode transformer) {
        removeMatches(target.fields, transformer.fields, field -> String.format("%s %s", field.name, field.desc), FIELDS);
        removeMatches(target.methods, transformer.methods, method -> String.format("%s%s", method.name, method.desc), METHODS);
    }
}
