package io.github.sianabeeblebrox.specter.impl;

import groovy.lang.GroovyClassLoader;
import groovyjarjarasm.asm.ClassReader;
import groovyjarjarasm.asm.ClassWriter;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;

import javax.annotation.Nullable;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.net.URI;
import java.security.CodeSource;
import java.security.Principal;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static io.github.sianabeeblebrox.specter.ExceptionUtil.*;
import static net.lenni0451.classtransform.utils.ASMUtils.slash;

/**
 * Groovy class loader that remembers compiled classes' bytecode so that
 * {@link GroovyByteClassLoader#getResourceAsStream(String)} can retrieve it later for class transforms.<br>
 * Also provides a hook for whenever a new class is defined.
 */
public class GroovyByteClassLoader extends GroovyClassLoader {
    private final ConcurrentHashMap<String, byte[]> BYTES = new ConcurrentHashMap<>();

    public GroovyByteClassLoader() {
        super();
    }

    public GroovyByteClassLoader(final ClassLoader parent) {
        super(parent);
    }

    public GroovyByteClassLoader(final ClassLoader parent, final CompilerConfiguration config) {
        super(parent, config, true);
    }

    private final List<ClassFileTransformer> transformers = new LinkedList<>();
    public void addTransformer(final ClassFileTransformer transformer) {
        transformers.add(transformer);
    }

    protected final byte[] applyTransformers(final URI uri, final String name, byte[] bytes) throws IllegalClassFormatException {
        final CodeSource cs = new CodeSource(unchecked(uri::toURL), (Certificate[]) null);
        final ProtectionDomain domain = new ProtectionDomain(cs, this.getPermissions(cs), this, new Principal[] {});
        for(final ClassFileTransformer transformer : this.transformers) {
            if((bytes = nonnull(ncls(transformer.transform(this.getUnnamedModule(), this, name, null, domain, bytes), bytes))).length == 0) {
                break;
            }
        }
        return bytes;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        if (this.BYTES.containsKey(name)) {
            return new ByteArrayInputStream(this.BYTES.get(name));
        }
        return super.getResourceAsStream(name);
    }

    @Override
    public Class<?> defineClass(@Nullable String name, byte[] bytes) throws ClassFormatError {
        final Class<?> clazz = super.defineClass(name, bytes);
        this.BYTES.put(slash(clazz.getName()) + ".class", bytes);
        return onClassDefined(clazz, null);
    }

    public Class<?> defineClass(final URI uri, final byte[] bytes) {
        final Class<?> clazz = super.defineClass(null, bytes, 0, bytes.length, new CodeSource(unchecked(() -> uri.toURL()), (Certificate[]) null));
        this.BYTES.put(slash(clazz.getName()) + ".class", bytes);
        return onClassDefined(clazz, null);
    }

    @Override
    protected ClassCollector createCollector(CompilationUnit unit, SourceUnit su) {
        return new BytecodeClassCollector(new InnerLoader(GroovyByteClassLoader.this), unit, su);
    }

    protected @Nullable byte[] onClassBytes(final URI uri, final @Nullable String name, final byte[] bytes) {
        final String n = ncls(name, () -> new ClassReader(bytes).getClassName());
        final byte[] b = unchecked(() -> applyTransformers(uri, n, bytes));
        if(b.length == 0) {
            return null;
        }
        this.BYTES.put(n + ".class", b);
        return b;
    }

    public Class<?> onClassDefined(final Class<?> clazz, final @Nullable SourceUnit source) {
        return clazz;
    }

    private final class BytecodeClassCollector extends ClassCollector {
        private final SourceUnit source;

        public BytecodeClassCollector(
                final InnerLoader loader,
                final CompilationUnit unit,
                final SourceUnit su
        ) {
            super(loader, unit, su);
            this.source = su;
        }

        @Override
        protected Class<?> createClass(final byte[] code, final ClassNode classNode) {
            // source.getSource().getURI()
            GroovyByteClassLoader.this.BYTES.put(slash(classNode.getName()) + ".class", code);
            return GroovyByteClassLoader.this.onClassDefined(super.createClass(code, classNode), this.source);
        }
    }
}
