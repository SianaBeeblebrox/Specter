package io.github.sianabeeblebrox.specter.impl;

import groovy.lang.GroovyClassLoader;
import groovyjarjarasm.asm.ClassWriter;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static net.lenni0451.classtransform.utils.ASMUtils.slash;

/**
 * Groovy class loader that remembers compiled classes' bytecode so that
 * {@link GroovyByteClassLoader#getResourceAsStream(String)} can retrieve it later for class transforms.<br>
 * Also provides a hook for whenever a new class is defined.
 */
public class GroovyByteClassLoader extends GroovyClassLoader {
    private final Map<String, byte[]> BYTES = new HashMap<>();

    public GroovyByteClassLoader() {
        super();
    }

    public GroovyByteClassLoader(final ClassLoader parent) {
        super(parent);
    }

    public GroovyByteClassLoader(final ClassLoader parent, final CompilerConfiguration config) {
        super(parent, config, true);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        if (this.BYTES.containsKey(name)) {
            return new ByteArrayInputStream(this.BYTES.get(name));
        }
        return super.getResourceAsStream(name);
    }

    @Override
    public Class defineClass(String name, byte[] bytes) throws ClassFormatError {
        final Class<?> clazz = super.defineClass(name, bytes);
        this.BYTES.put(slash(clazz.getName()) + ".class", bytes);
        return onClassDefined(clazz, null);
    }

    @Override
    protected ClassCollector createCollector(CompilationUnit unit, SourceUnit su) {
        return new BytecodeClassCollector(this.BYTES, new InnerLoader(GroovyByteClassLoader.this), unit, su);
    }

    public Class<?> onClassDefined(final Class<?> clazz, final @Nullable SourceUnit source) {
        return clazz;
    }

    private final class BytecodeClassCollector extends ClassCollector {
        private final Map<String, byte[]> BYTES;
        private final SourceUnit SOURCE;

        public BytecodeClassCollector(
                final Map<String, byte[]> bytes,
                final InnerLoader loader,
                final CompilationUnit unit,
                final SourceUnit su
        ) {
            super(loader, unit, su);
            this.SOURCE = su;
            this.BYTES = bytes;
        }

        @Override
        protected Class<?> onClassNode(final ClassWriter classWriter, final ClassNode classNode) {
            this.BYTES.put(slash(classNode.getName()) + ".class", classWriter.toByteArray());
            return GroovyByteClassLoader.this.onClassDefined(super.onClassNode(classWriter, classNode), this.SOURCE);
        }
    }
}
