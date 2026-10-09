package io.github.sianabeeblebrox.specter.config;

import groovy.lang.Tuple3;
import groovy.lang.groovydoc.Groovydoc;
import groovy.lang.groovydoc.GroovydocHolder;
import io.github.sianabeeblebrox.specter.config.impl.Expressions;
import io.github.sianabeeblebrox.specter.config.impl.WrappedExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.syntax.SyntaxException;

import javax.annotation.Nullable;
import javax.lang.model.SourceVersion;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.sianabeeblebrox.specter.ExceptionUtil.throwUnchecked;

public final class ConfigMap extends AbstractMap<Object, Object> implements WrappedExpression {
    private final MapExpression expression;
    private final LinkedHashMap<Object, Object> values;
    private final AbstractSet<Entry<Object, Object>> entries;

    private ConfigMap(final Groovydoc groovydoc, final List<Tuple3<Groovydoc, Config.Entry, Config.Entry>> entries) {
        this.expression = new MapExpression(entries.stream().map(entry -> {
            final MapEntryExpression expr = new MapEntryExpression(entry.getV2().expression(), entry.getV3().expression());
            expr.putNodeMetaData(GroovydocHolder.DOC_COMMENT, entry.getV1());
            return expr;
        }).collect(Collectors.toCollection(ArrayList::new)));
        this.expression.putNodeMetaData(GroovydocHolder.DOC_COMMENT, groovydoc);

        this.values = new LinkedHashMap<>();
        for(final Tuple3<Groovydoc, Config.Entry, Config.Entry> entry : entries) {
            if(values.containsKey(entry.getV2().value())) {
                throwUnchecked(new SyntaxException(String.format("Expected unique key '%s'", entry.getV2().value()), entry.getV2().expression()));
            }

            values.put(entry.getV2().value(), entry.getV3().value()); // Map.entry doesn't allow null
        }

        this.entries = new AbstractSet<>() {
            @Override
            public Iterator<Entry<Object, Object>> iterator() {
                return new Iterator<>() {
                    private final Iterator<Entry<Object, Object>> iterator = ConfigMap.this.values.entrySet().iterator();
                    private Entry<Object, Object> current;

                    @Override
                    public boolean hasNext() {
                        return this.iterator.hasNext();
                    }

                    @Override
                    public Entry<Object, Object> next() {
                        final Entry<Object, Object> next = this.iterator.next();
                        return this.current = new Entry<>() {
                            @Override
                            public Object getKey() {
                                return next.getKey();
                            }

                            @Override
                            public Object getValue() {
                                return next.getValue();
                            }

                            @Override
                            public Object setValue(final Object value) {
                                return ConfigMap.this.put(this.getKey(), value);
                            }

                            @Override
                            public boolean equals(final Object obj) {
                                if (obj == this)
                                    return true;

                                return obj instanceof Map.Entry<?, ?> e
                                        && Objects.equals(this.getKey(), e.getKey())
                                        && Objects.equals(this.getValue(), e.getValue());
                            }

                            @Override
                            public int hashCode() {
                                return Objects.hashCode(this.getKey()) ^ Objects.hashCode(this.getValue());
                            }

                            @Override
                            public String toString() {
                                return this.getKey() + "=" + this.getValue();
                            }
                        };
                    }

                    @Override
                    public void remove() {
                        if(this.current == null) throw new IllegalStateException();
                        ConfigMap.this.remove(current.getKey());
                        this.current = null;
                    }
                };
            }

            @Override
            public int size() {
                return ConfigMap.this.values.size();
            }
        };
    }

    public ConfigMap(final MapExpression expression) {
        this(
                expression.getGroovydoc(),
                expression.getMapEntryExpressions().stream().map(entry -> new Tuple3<>(entry.getGroovydoc(), Config.entry(entry.getKeyExpression()), Config.entry(entry.getValueExpression()))).toList()
        );
    }

    public ConfigMap(final Map<Object, Object> map) {
        this(
                Groovydoc.EMPTY_GROOVYDOC,
                map.entrySet().stream().map(entry -> new Tuple3<>(Groovydoc.EMPTY_GROOVYDOC, Config.entry(entry.getKey()), Config.entry(entry.getValue()))).toList()
        );
    }

    public ConfigMap() {
        this(new MapExpression());
    }

    private int indexOf(final Object key) {
        return List.copyOf(this.values.keySet()).indexOf(key);
    }

    @Override
    public Expression getExpression() {
        return this.expression;
    }

    @Override
    public boolean containsValue(final Object value) {
        return this.values.containsValue(value);
    }

    @Override
    public boolean containsKey(final Object key) {
        return this.values.containsKey(key);
    }

    @Override
    public Object get(final Object key) {
        return this.values.get(key);
    }

    @Override
    public Object put(final Object key, final Object value) {
        final int index = this.indexOf(key);
        final Config.Entry entry = Config.entry(value);
        if(index != -1) {
            this.expression.getMapEntryExpressions().get(index).setValueExpression(entry.expression());
            return this.values.put(key, entry.value());
        } else {
            this.expression.getMapEntryExpressions().add(new MapEntryExpression(new ConstantExpression(key), entry.expression()));
            return this.values.put(key, entry.value());
        }
    }

    @Override
    public Object remove(final Object key) {
        final int index = this.indexOf(key);
        if(index != -1) {
            this.expression.getMapEntryExpressions().remove(index);
            return this.values.remove(key);
        }
        return null;
    }

    @Override
    public void clear() {
        this.expression.getMapEntryExpressions().clear();
        this.values.clear();
    }

    @Override
    public Set<Entry<Object, Object>> entrySet() {
        return this.entries;
    }

    public @Nullable String getComment(final Object key) {
        final int index = this.indexOf(key);
        if(index != -1) {
            return WrappedExpression.getComment(this.expression.getMapEntryExpressions().get(index));
        } else {
            return null;
        }
    }

    public void putComment(final Object key, final @Nullable String comment) {
        final int index = this.indexOf(key);
        if(index != -1) {
            WrappedExpression.setComment(this.expression.getMapEntryExpressions().get(index), comment);
        }
    }

    public void merge(final Map<Object, Object> map) {
        for(final var entry : map.entrySet()) {
            if(!this.containsKey(entry.getKey())) {
                this.put(entry.getKey(), entry.getValue());
                if(map instanceof ConfigMap) {
                    this.putComment(entry.getKey(), ((ConfigMap) map).getComment(entry.getKey()));
                }
            } else if(this.get(entry.getKey()) instanceof final ConfigMap child && entry.getValue() instanceof final Map other) {
                child.merge(other);
            }
        }
    }


    private static <T> Stream<T> concat(final Stream<T>... streams) {
        return Stream.of(streams).flatMap(t -> t);
    }

    @Override
    public String stringify() {
        return (WrappedExpression.getRawComment(this.expression) instanceof final String comment ? comment + "\n" : "")
                + (
                        this.isEmpty() ? "[:]" : concat(
                                Stream.of("["),
                                this.expression.getMapEntryExpressions().stream().map(entry ->
                                        (WrappedExpression.getRawComment(entry) instanceof final String comment ? comment + "\n" : "")
                                        + (
                                                entry.getKeyExpression() instanceof final ConstantExpression constant
                                                && constant.getValue() instanceof final String text
                                                && SourceVersion.isIdentifier(text) && !SourceVersion.isKeyword(text)
                                                        ? text
                                                        : Expressions.stringify(entry.getKeyExpression())
                                        )
                                        + ": "
                                        + Config.stringify(entry.getValueExpression())
                                ).collect(Collectors.joining(",\n")).lines().map(line -> "    " + line),
                                Stream.of("]")
                        ).collect(Collectors.joining("\n"))
                );
    }
}
