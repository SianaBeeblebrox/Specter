package io.github.sianabeeblebrox.specter.config.impl;

import groovy.lang.groovydoc.Groovydoc;
import groovy.lang.groovydoc.GroovydocHolder;
import groovyjarjarantlr4.v4.runtime.CommonTokenStream;
import groovyjarjarantlr4.v4.runtime.Token;
import io.github.sianabeeblebrox.specter.Dynamics;
import org.apache.groovy.parser.antlr4.*;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.control.SourceUnit;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * An AstBuilder that attaches doc-comments to maps literals, list literals, and map entries
 */
public class StickyAstBuilder extends AstBuilder {
    private final List<Token> tokens;
    private final Map<Map.Entry</*line*/ Integer, /*col*/ Integer>, /*index*/ Integer> index;

    public StickyAstBuilder(final SourceUnit sourceUnit) {
        super(sourceUnit, true, false);
        final CommonTokenStream cts = ((CommonTokenStream) Dynamics.<GroovyLangParser>get(this, AstBuilder.class, "parser").getInputStream());
        cts.fill();
        this.tokens = new ArrayList<>(cts.getTokens());
        this.index = IntStream.range(0, this.tokens.size()).mapToObj(i -> Map.entry(Map.entry(this.tokens.get(i).getLine(), this.tokens.get(i).getCharPositionInLine() + 1), i)).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (p,c)-> p, HashMap::new));
    }

    private <T extends ASTNode> T handle(final T t) {
        if(t instanceof final AnnotatedNode node) {
            if(this.index.get(Map.entry(t.getLineNumber(), t.getColumnNumber())) instanceof Integer index) {
                while(index-- > 0 && tokens.get(index).getType() == GroovyLangLexer.NL) {
                    if(tokens.get(index).getText().startsWith("/**")) {
                        node.putNodeMetaData(GroovydocHolder.DOC_COMMENT, new Groovydoc(tokens.get(index).getText().strip().lines().map(String::strip).collect(Collectors.joining("\n")), node));
                        break;
                    }
                }
            }
        }

        return t;
    }

    @Override
    public MapExpression visitMap(final GroovyParser.MapContext ctx) {
        return this.handle(super.visitMap(ctx));
    }

    @Override
    public MapEntryExpression visitMapEntry(final GroovyParser.MapEntryContext ctx) {
        return this.handle(super.visitMapEntry(ctx));
    }

    @Override
    public ListExpression visitList(final GroovyParser.ListContext ctx) {
        return this.handle(super.visitList(ctx));
    }
}
