package io.github.sianabeeblebrox.specter.annotations;

import net.lenni0451.classtransform.mappings.annotation.AnnotationRemap;
import net.lenni0451.classtransform.mappings.annotation.RemapType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Redirect a specified method call or field access.<br>
 * The following target types are supported:<br>
 * <ul>
 *     <li>INVOKE</li>
 *     <li>FIELD</li>
 *     <li>GETFIELD</li>
 *     <li>PUTFIELD</li>
 *     <li>NEW</li>
 * </ul>
 * <p>When redirecting a method call:</p>
 * <ul>
 *     <li>The transformer method must have the same parameters and return type as the redirected method.</li>
 *     <li>If the redirected method is not static, the instance of the method owner has to be the first parameter of the transformer method.</li>
 * </ul>
 * <p>When redirecting a field access:</p>
 * <ul>
 *     <li>The transformer method must have the same return type as the redirected field.</li>
 *     <li>If the redirected field is not static the instance of the field owner has to be the first parameter of the transformer method.</li>
 *     <li>No other parameters than the non-static instance are allowed.</li>
 * </ul>
 * The transformer method has to be static if the target method/field is static.<br>
 * <br>
 * Redirecting a method call:<br>
 * <pre>
 * &#64;Redirect(method = "print", target = &#64;Target(value = "INVOKE", target = "..."))
 * public String redirect(Object instance, int arg1, String arg2) {
 *     //Do something with the arguments and return the new value
 * }
 * </pre>
 * Redirecting a field access:<br>
 * <pre>
 * &#64;Redirect(method = "print", target = &#64;Target(value = "GETFIELD", target = "..."))
 * public String redirect(Object instance) {
 *     //Do something with the instance and return the new value
 * }
 * </pre>
 * See {@link Target} for more information about targets.<br>
 * If your target has to be chosen more precisely you can use a {@link #slice()} to narrow down the search.
 *
 * @see Target
 * @see Slice
 * @see <a href="https://github.com/Lenni0451/ClassTransform/wiki/CRedirect">GitHub Wiki</a>
 */
@Retention(RetentionPolicy.CLASS)
@java.lang.annotation.Target(ElementType.METHOD)
public @interface Redirect {

    /**
     * The method name and descriptor to inject into.<br>
     * This supports multiple targets and wildcards.<br>
     * e.g. {@code print(Ljava/lang/String;)V} or {@code print*}
     *
     * @return The method name and descriptor
     */
    @AnnotationRemap(value = RemapType.SHORT_MEMBER, allowClassPrefix = true)
    String[] method();

    /**
     * The target for the redirect.
     *
     * @return The target
     */
    @AnnotationRemap(RemapType.ANNOTATION)
    Target target();

    /**
     * The slice to narrow down the search for the target.
     *
     * @return The slice
     */
    @AnnotationRemap(RemapType.ANNOTATION)
    Slice slice() default @Slice;

}
