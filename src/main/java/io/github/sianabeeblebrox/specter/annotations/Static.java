package io.github.sianabeeblebrox.specter.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Makes the annotated transformer method static.<br>
 * Needed when targeting static interface methods from Groovy.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.METHOD)
public @interface Static {
}
