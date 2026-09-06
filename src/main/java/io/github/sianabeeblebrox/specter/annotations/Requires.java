package io.github.sianabeeblebrox.specter.annotations;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

/**
 * Removes the annotated element unless the given class exists (or vice versa if inverted).<br>
 * Works with {@link Transformer} and is useful for managing optional dependencies and differing client/server
 * applications. Multiple {@link Requires} annotations on the same element combine as logical AND.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
@GroovyASTTransformationClass({"io.github.sianabeeblebrox.specter.annotations.impl.RequiresTransformation"})
public @interface Requires {
    /**
     * The name of the class to check for.<br>
     * This requires the complete name including the package separated by a dot.<br>
     *
     * @return The name of the class to check for
     */
    String name();

    /**
     * If {@code true}, then the annotated element is removed iff the given class exists; otherwise, it is remove if the
     * class does not exist.
     * @return whether to invert the default logic
     */
    boolean inverted() default false;
}
