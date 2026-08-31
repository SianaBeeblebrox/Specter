package io.github.sianabeeblebrox.specter.annotations;

import net.lenni0451.classtransform.mappings.annotation.AnnotationRemap;
import net.lenni0451.classtransform.mappings.annotation.RemapType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Overwrite a method in a class.<br>
 * The transformer method must have the same parameters and return type as the overwritten method.<br>
 * The access of the transformer method has to be higher or equal to the overwritten method (private {@literal <} package private {@literal <} protected {@literal <} public).<br>
 * If the overwritten method is static, the transformer method has to be static as well.<br>
 * <br>
 * Overwrite a method:<br>
 * <pre>
 * &#64;Overwrite
 * public static void print(String text) {
 *     //Do something
 * }
 * </pre>
 *
 * @see <a href="https://github.com/Lenni0451/ClassTransform/wiki/COverride">GitHub Wiki</a>
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface Overwrite {

    /**
     * The method name and descriptor to inject into.<br>
     * This supports multiple targets and wildcards.<br>
     * e.g. {@code print(Ljava/lang/String;)V} or {@code print*}
     *
     * @return The method name and descriptor
     */
    @AnnotationRemap(value = RemapType.SHORT_MEMBER, allowClassPrefix = true)
    String[] value() default {};

}
