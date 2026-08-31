package io.github.sianabeeblebrox.specter.annotations;

import net.lenni0451.classtransform.mappings.annotation.AnnotationRemap;
import net.lenni0451.classtransform.mappings.annotation.RemapType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Specify a slice of bytecode to make choosing the injection target easier.
 *
 * @see <a href="https://github.com/Lenni0451/ClassTransform/wiki/CSlice">GitHub Wiki</a>
 */
@Retention(RetentionPolicy.CLASS)
@java.lang.annotation.Target(ElementType.ANNOTATION_TYPE)
public @interface Slice {

    /**
     * The start of the slice.<br>
     * The target <b>must</b> match only a single instruction.
     *
     * @return The start of the slice
     */
    @AnnotationRemap(RemapType.ANNOTATION)
    Target from() default @Target("");

    /**
     * The end of the slice.<br>
     * The target <b>must</b> match only a single instruction.
     *
     * @return The end of the slice
     */
    @AnnotationRemap(RemapType.ANNOTATION)
    Target to() default @Target("");

}
