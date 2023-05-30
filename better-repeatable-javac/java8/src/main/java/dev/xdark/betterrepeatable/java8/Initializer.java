package dev.xdark.betterrepeatable.java8;

import com.sun.tools.javac.code.SymbolMetadata;
import dev.xdark.betterrepeatable.InstrumentationProvider;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

import static org.objectweb.asm.Opcodes.*;

public final class Initializer {

	public static void init() {
		ClassLoader compilerClassLoader = SymbolMetadata.class.getClassLoader();
		// Bulletproof (I hope) hack to not do transformation
		// multiple times if javac classes are loaded
		// from the same class loader, but plugin classes
		// from another.
		try {
			Class.forName("dev.xdark.betterrepeatable.java8.LoadStub", false, compilerClassLoader);
			return;
		} catch (ClassNotFoundException ignored) {
		}
		Instrumentation instrumentation = InstrumentationProvider.get();
		ClassLoader pluginClassLoader = Initializer.class.getClassLoader();
		try {
			compilerClassLoader.loadClass("dev.xdark.betterrepeatable.java8.SymbolMetadataPatch");
		} catch (ClassNotFoundException ignored) {
			try (InputStream in = pluginClassLoader.getResourceAsStream("dev/xdark/betterrepeatable/java8/SymbolMetadataPatch.class")) {
				byte[] bytes = readStreamBytes(in);
				Method m = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
				m.setAccessible(true);
				m.invoke(compilerClassLoader, null, bytes, 0, bytes.length);
			} catch (ReflectiveOperationException | IOException ex) {
				throw new RuntimeException("Unable to load SymbolMetadataPatch", ex);
			}
		}
		ClassFileTransformer cf = new ClassFileTransformer() {
			@Override
			public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
				if ("com/sun/tools/javac/code/SymbolMetadata".equals(className)) {
					try {
						ClassReader reader = new ClassReader(classfileBuffer);
						ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES) {
							@Override
							protected ClassLoader getClassLoader() {
								return compilerClassLoader;
							}
						};
						reader.accept(new ClassVisitor(ASM9, writer) {

							@Override
							public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
								MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
								if ("getAttributesForCompletion".equals(name) && "(Lcom/sun/tools/javac/comp/Annotate$AnnotateRepeatedContext;)Lcom/sun/tools/javac/util/List;".equals(descriptor)) {
									mv = new MethodVisitor(ASM9, mv) {

										@Override
										public void visitCode() {
											super.visitCode();
											visitVarInsn(ALOAD, 0);
											visitVarInsn(ALOAD, 1);
											visitMethodInsn(INVOKESTATIC, "dev/xdark/betterrepeatable/java8/SymbolMetadataPatch", "getAttributesForCompletion", "(Lcom/sun/tools/javac/code/SymbolMetadata;Lcom/sun/tools/javac/comp/Annotate$AnnotateRepeatedContext;)Lcom/sun/tools/javac/util/List;", false);
											Label skip = new Label();
											visitInsn(DUP);
											visitJumpInsn(IFNULL, skip);
											visitInsn(ARETURN);
											visitLabel(skip);
											visitInsn(POP);
										}
									};
								}
								return mv;
							}
						}, 0);
						return writer.toByteArray();
					} finally {
						instrumentation.removeTransformer(this);
					}
				}
				return null;
			}
		};
		instrumentation.addTransformer(cf, true);
		try {
			instrumentation.retransformClasses(SymbolMetadata.class);
		} catch (UnmodifiableClassException e) {
			throw new IllegalStateException("Cannot patch SymbolMetadata", e);
		} catch (InternalError | VerifyError e) {
			throw new RuntimeException("Failed to retransform SymbolMetadata, SymbolMetadataPatch did not load?", e);
		}
		try (InputStream in = pluginClassLoader.getResourceAsStream("dev/xdark/betterrepeatable/java8/LoadStub.class")) {
			byte[] bytes = readStreamBytes(in);
			Method m = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
			m.setAccessible(true);
			m.invoke(compilerClassLoader, null, bytes, 0, bytes.length);
		} catch (ReflectiveOperationException | IOException ignored) {
			// Ugh...
		}
	}

	private static byte[] readStreamBytes(InputStream in) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		byte[] buf = new byte[8192];
		int r;
		while ((r = in.read(buf)) != -1) {
			baos.write(buf, 0, r);
		}
		return baos.toByteArray();
	}
}
