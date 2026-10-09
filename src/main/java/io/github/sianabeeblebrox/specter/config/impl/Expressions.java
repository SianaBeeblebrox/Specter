package io.github.sianabeeblebrox.specter.config.impl;

import groovy.console.ui.AstNodeToScriptVisitor;
import groovy.lang.*;
import io.github.sianabeeblebrox.specter.Dynamics;
import org.apache.groovy.parser.antlr4.AstBuilder;
import org.apache.groovy.parser.antlr4.GroovyLangParser;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.Phases;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.github.sianabeeblebrox.specter.ExceptionUtil.unchecked;

public final class Expressions {
    private static int c = 0;
    public static Object eval(final Expression expression, final GroovyClassLoader cl) {
        if(expression instanceof final ConstantExpression constant) {
            return constant.getValue();
        } else {
            final var cu = new CompilationUnit(cl);
            cu.addSource("eval$" + Integer.toString(c++, 36), "");
            cu.compile(Phases.CONVERSION);
            cu.getAST().getClasses().getFirst().getMethods("run").getFirst().setCode(new ExpressionStatement(expression));
            cu.compile(Phases.CLASS_GENERATION);
            final Class<?> clazz = cu.getClasses().stream().map(c -> cu.getClassLoader().defineClass(null, c.getBytes())).toList().getFirst();

            return unchecked(() -> ((GroovyObject) clazz.getDeclaredConstructor().newInstance())).invokeMethod("run", null);
        }
    }

    public static Expression parse(final String expression) {
        final AstBuilder builder = new AstBuilder(new CompilationUnit(null, null, null).addSource("", expression), false, false);
        return (Expression) builder.visit(Dynamics.<GroovyLangParser>get(builder, "parser").expression());
    }

    public static String stringify(final Expression expression) {
        final var sw = new StringWriter();
        new AstNodeToScriptVisitor(sw).visit(expression);
        return sw.toString();
    }

    public static Expression uneval(final Object value) {
        return switch(value) {
            case final Enum<?> enumeration -> new PropertyExpression(new ClassExpression(new ClassNode(enumeration.getClass())), enumeration.name());
            case final EmptyRange<?> range -> new RangeExpression(new ConstantExpression(Dynamics.get(range, "at")), new ConstantExpression(Dynamics.get(range, "at")), false);
            case final NumberRange range -> new RangeExpression(new ConstantExpression(range.getFrom()), new ConstantExpression(range.getTo()), !Dynamics.<Boolean>get(range, "inclusiveLeft"), !Dynamics.<Boolean>get(range, "inclusiveRight"));
            case final IntRange range -> new RangeExpression(new ConstantExpression(range.getFrom()), new ConstantExpression(range.getTo()), !Dynamics.<Boolean>get(range, "inclusiveLeft"), !Dynamics.<Boolean>get(range, "inclusiveRight"));
            case final Record record -> new ConstructorCallExpression(new ClassNode(record.getClass()), new TupleExpression((List<Expression>) Arrays.stream(record.getClass().getRecordComponents()).map(component -> uneval(unchecked(() -> component.getAccessor().invoke(record)))).collect(Collectors.toCollection(ArrayList::new))));
            case final Closure<?> closure -> {
                final MethodNode doCall = closure.getMetaClass().getClassNode().getDeclaredMethods("doCall").getFirst();
                yield new ClosureExpression(doCall.getParameters(), doCall.getCode());
            }
            case final Tuple<?> tuple -> new TupleExpression(tuple.stream().map(Expressions::uneval).toList());
            case final GString gstring -> new GStringExpression(null, Arrays.stream(gstring.getStrings()).map(ConstantExpression::new).collect(Collectors.toCollection(ArrayList::new)),  Arrays.stream(gstring.getValues()).map(Expressions::uneval).collect(Collectors.toCollection(ArrayList::new)));
            case final List<?> list -> new ListExpression(list.stream().map(Expressions::uneval).collect(Collectors.toCollection(ArrayList::new)));
            case final Map<?, ?> map -> new MapExpression(map.entrySet().stream().map(entry -> new MapEntryExpression(uneval(entry.getKey()), uneval(entry.getValue()))).collect(Collectors.toCollection(ArrayList::new)));
            case null, default -> new ConstantExpression(value);
        };
    }
}
