package io.github.sianabeeblebrox.specter.config;

import groovy.lang.groovydoc.Groovydoc;
import groovy.lang.groovydoc.GroovydocHolder;
import io.github.sianabeeblebrox.specter.config.impl.WrappedExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A wrapped list literal that preserves inner AST structure (See {@link Config})
 */
public final class ConfigList extends AbstractList<Object> implements WrappedExpression {
    private final ListExpression expression;
    private final List<Object> values;

    private ConfigList(final Groovydoc groovydoc, final List<Config.Entry> entries) {
        this.expression = new ListExpression(entries.stream().map(Config.Entry::expression).collect(Collectors.toCollection(ArrayList::new)));
        this.expression.putNodeMetaData(GroovydocHolder.DOC_COMMENT, groovydoc);

        this.values = entries.stream().map(Config.Entry::value).collect(Collectors.toCollection(ArrayList::new));
    }

    public ConfigList(final Collection<Object> collection) {
        this(Groovydoc.EMPTY_GROOVYDOC, collection.stream().map(Config::entry).toList());
    }

    public ConfigList(final ListExpression expression) {
        this(expression.getGroovydoc(), expression.getExpressions().stream().map(Config::entry).toList());
    }

    public ConfigList() {
        this(new ListExpression());
    }

    @Override
    public int size() {
        return this.values.size();
    }

    @Override
    public Object get(final int index) {
        return this.values.get(index);
    }

    @Override
    public Object set(final int index, final Object element) {
        final Config.Entry entry = Config.entry(element);
        this.expression.getExpressions().set(index, entry.expression());
        return this.values.set(index, entry.value());
    }

    @Override
    public void add(final int index, final Object element) {
        final Config.Entry entry = Config.entry(element);
        this.expression.getExpressions().add(index, entry.expression());
        this.values.add(index, entry.value());
    }

    @Override
    public Object remove(int index) {
        this.expression.getExpressions().remove(index);
        return this.values.remove(index);
    }

    @Override
    public void clear() {
        this.expression.getExpressions().clear();
        this.values.clear();
    }

    @Override
    public boolean contains(final Object o) {
        return this.values.contains(o);
    }

    @Override
    public Expression getExpression() {
        return this.expression;
    }

    @Override
    public String stringify() {
        return (WrappedExpression.getRawComment(this.expression) instanceof final String comment ? comment + "\n" : "")
                + "[" + this.expression.getExpressions().stream().map(Config::stringify).collect(Collectors.joining(", ")) + "]";
    }
}
