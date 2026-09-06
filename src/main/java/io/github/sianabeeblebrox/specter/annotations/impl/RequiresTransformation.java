package io.github.sianabeeblebrox.specter.annotations.impl;

import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import java.io.IOException;
import java.io.InputStream;

import static net.lenni0451.classtransform.utils.ASMUtils.slash;

@SuppressWarnings("unused")
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public final class RequiresTransformation implements ASTTransformation {
    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        final AnnotationNode annotation = (AnnotationNode) nodes[0];
        final String name = annotation.getMember("name") instanceof final ConstantExpression expr && expr.getValue() instanceof final String str ? str : null;
        final boolean inverted = annotation.getMember("inverted") instanceof final ConstantExpression expr && expr.isTrueExpression();

        if(name == null) {
            source.addFatalError("Expected string class name for 'name'", annotation);
        }

        if(hasClass(source.getClassLoader(), name) == inverted) {
            switch(nodes[1]) {
                case final MethodNode node -> node.getDeclaringClass().removeMethod(node);
                case final ClassNode node -> {
                    if(source.getAST() instanceof final ModuleNode module) {
                        module.getClasses().remove(node);
                    }
                }
                default -> source.addFatalError("Expected class or method", annotation);
            }
        }
    }

    private static boolean hasClass(final ClassLoader cl, final String name) {
        try(final InputStream stream = cl.getResourceAsStream(slash(name) + ".class")) {
            return stream != null;
        } catch(final IOException exception) {
            return false;
        }
    }

}
