package io.github.sianabeeblebrox.specter.config.impl;

import groovy.lang.groovydoc.Groovydoc;
import groovy.lang.groovydoc.GroovydocHolder;
import io.github.sianabeeblebrox.specter.config.Config;
import org.codehaus.groovy.ast.expr.Expression;

import javax.annotation.Nullable;
import java.util.stream.Collectors;

public interface WrappedExpression {
    /**
     * Gets the underlying expression wrapped by this value
     * @return the expression
     */
    Expression getExpression();

    /**
     * Serializes this value to a string
     * @return the serialized string
     */
    default String stringify() {
        return Config.stringify(this.getExpression());
    }

    /**
     * Sets a doc-comment on this value
     * @param comment the comment text (without any <code>/** *** *&#47;</code>) or {@code null} to remove the comment
     */
    default void setComment(final @Nullable String comment) {
        setComment(this.getExpression(), comment);
    }

    /**
     * Gets the doc-comment on this value if any
     * @return the comment text (without any <code>/** *** *&#47;</code>) or {@code null} if no comment
     */
    default @Nullable String getComment() {
        return getComment(this.getExpression());
    }

    static void setComment(final Expression expression, final @Nullable String comment) {
        expression.putNodeMetaData(
                GroovydocHolder.DOC_COMMENT,
                comment == null
                    ? Groovydoc.EMPTY_GROOVYDOC
                    : new Groovydoc(
                            "/**\n" + comment.lines().map(line -> " * " + line).collect(Collectors.joining("\n")) + "\n */",
                            expression
                    )
        );
    }

    static @Nullable String getComment(final Expression expression) {
        return expression.getGroovydoc().isPresent()
                ? expression
                    .getGroovydoc()
                    .getContent()
                    .replaceAll("^/\\*\\*\\n?|(?<=\\n)\\s*?\\*(?!/)\\s?|\\n?\\s*?\\*/", "")
                : null
        ;
    }

    static @Nullable String getRawComment(final Expression expression) {
        return expression.getGroovydoc().isPresent() ? expression.getGroovydoc().getContent() : null;
    }
}
