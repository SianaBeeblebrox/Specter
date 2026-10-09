package io.github.sianabeeblebrox.specter.config;

import groovy.lang.*;
import io.github.sianabeeblebrox.specter.Specter;
import io.github.sianabeeblebrox.specter.config.impl.Expressions;
import io.github.sianabeeblebrox.specter.config.impl.StickyAstBuilder;
import io.github.sianabeeblebrox.specter.config.impl.WrappedExpression;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.SyntaxException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.cert.Certificate;
import java.util.*;

import static io.github.sianabeeblebrox.specter.ExceptionUtil.unchecked;
import static io.github.sianabeeblebrox.specter.config.impl.Expressions.*;

// TODO give access to previous values and root when evaluating

/**
 * Methods for reading and writing objects with Groovy-based config files.
 *
 * <h1>Config Format</h1>
 * Configuration files should contain a single Groovy expression with an optional doc-comment. {@link Map} and
 * {@link List} literals not within a different expression are wrapped in {@link ConfigMap} and {@link ConfigList}
 * respectively. These wrappers will preserve the AST structure of their values unless overwritten. Doc-comments attached
 * to these lists, maps, and map entries are also preserved and can be programmatically manipulated via
 * {@link WrappedExpression#setComment(String) setComment(String)} and {@link WrappedExpression#getComment() getComment()}.
 * Entries in these lists and maps can be assigned a Groovy {@link Expression} or an arbitrary value. When assigning an
 * expression, it is evaluated to get the new value and the underlying expression is stored for serialization. When
 * assigning any other value, an attempt is made to create an expression representing it for serialization. Primitives,
 * boxed primitives, {@link Enum enums}, {@link Record records}, {@link String strings}, {@link GString gstrings},
 * numeric {@link Range ranges}, {@link Tuple tuples}, {@link Map maps}, and {@link List lists} are fully supported.
 * Groovy {@link Closure closures} are supported if the corresponding source is still available.
 *
 * <h1>Example</h1>
 * <pre><code>
 * /**
 *  * Example Config File
 *  *&#47;
 * [
 *     /**
 *      * User's first and last name
 *      *&#47;
 *     name: 'Jane Doe',
 *     age: 23,
 *     details: [
 *         favorite_color: new com.example.Color(0xff00ff),
 *         favorite_number: 11+2*3
 *     ],
 *     tags: ['uwu', 'o/']
 * ]
 * </code></pre>
 * <p><i>Warning: While a powerful intended feature, parsing config can execute arbitrary Groovy code!</i></p>
 * <br>
 * <p><i>Note: File extension is irrelevant for the purposes of this class; however, don't use one that Specter will load
 * automatically if placing configuration files within the mods folder! Try creating a file association in your editor
 * if you want syntax highlighting. </i></p>
 */
@ParametersAreNonnullByDefault
public final class Config {
    private static final GroovyClassLoader CLASS_LOADER = new GroovyClassLoader(Specter.getClassLoader());

    /**
     * Parses and loads a value from a string (See {@link Config})
     * @param source the text to parse
     * @return the parsed value
     */
    public static @Nullable Object parse(final String source) {
        return Config.parse(new CompilationUnit(null, null, Config.CLASS_LOADER).addSource("", source));
    }

    /**
     * Parses and loads a value from a file (See {@link Config})
     * @param uri the file to parse
     * @return the parsed value
     */
    public static @Nullable Object read(final URI uri) {
        return unchecked(() -> {
            final URL url = uri.toURL();
            return Config.parse(new CompilationUnit(null, new CodeSource(url, (Certificate[]) null), Config.CLASS_LOADER).addSource(url));
        });
    }

    /**
     * Parses and loads a value from a file (See {@link Config})
     * @param path the file to parse
     * @return the parsed value
     */
    public static @Nullable Object read(final String path) {
        return Config.read(Path.of(path));
    }

    /**
     * Parses and loads a value from a file (See {@link Config})
     * @param path the file to parse
     * @return the parsed value
     */
    public static @Nullable Object read(final Path path) {
        return Config.read(unchecked(path::toUri));
    }

    private static @Nullable  Object parse(final SourceUnit source) {
        return unchecked(() -> {
            final BlockStatement ast = new StickyAstBuilder(source).buildAST().getStatementBlock();
            final List<Statement> statements = ast.getStatements();

            if(statements.size() == 1 && statements.getFirst() instanceof final ExpressionStatement statement) {
                return Config.entry(statement.getExpression()).value();
            } else {
                throw new SyntaxException("Expected expression", statements.size() == 1 ? statements.getFirst() : ast);
            }
        });
    }

    /**
     * Serializes a value to a string (See {@link Config})
     * @param value the value to serialize
     * @return the serialized string
     */
    public static String stringify(final @Nullable Object value) {
        final Config.Entry entry = Config.entry(value);
        return entry.value() instanceof final WrappedExpression wrapped ? wrapped.stringify() : Expressions.stringify(entry.expression());
    }

    /**
     * Serializes a value to a file (See {@link Config})
     * @param value the value to serialize
     */
    public static void write(final Path path, final @Nullable Object value) {
        unchecked(() -> Files.writeString(path, stringify(value)));
    }

    /**
     * Serializes a value to a file (See {@link Config})
     * @param value the value to serialize
     */
    public static void write(final String path, final @Nullable Object value) {
        write(Path.of(path), value);
    }

    record Entry(@Nonnull Expression expression, @Nullable Object value) {}
    static Entry entry(final @Nullable Object value) {
        return switch(value) {
            case final LambdaExpression lambda -> entry(new ClosureExpression(lambda.getParameters(), lambda.getCode()));

            case final WrappedExpression wrapped -> new Entry(wrapped.getExpression(), wrapped);
            case final MapExpression map -> entry(new ConfigMap(map));
            case final Map map -> entry(new ConfigMap(map));
            case final ListExpression list -> entry(new ConfigList(list));
            case final List list when !(list instanceof Range<?>) -> entry(new ConfigList(list));

            case final Expression expression -> new Entry(expression, eval(expression, Config.CLASS_LOADER));
            case null, default -> new Entry(uneval(value), value);
        };
    }
}
