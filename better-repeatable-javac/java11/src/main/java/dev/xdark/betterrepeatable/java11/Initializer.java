package dev.xdark.betterrepeatable.java11;

import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.comp.Check;
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
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;

import static org.objectweb.asm.Opcodes.*;

public final class Initializer {

	public static void init() {
		ClassLoader compilerClassLoader;
		try {
			compilerClassLoader = Class.forName("com.sun.tools.javac.comp.Annotate", false, Initializer.class.getClassLoader()).getClassLoader();
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Cannot find Annotate class", e);
		}
		// Bulletproof (I hope) hack to not do transformation
		// multiple times if javac classes are loaded
		// from the same class loader, but plugin classes
		// from another.
		try {
			Class.forName("dev.xdark.betterrepeatable.java11.LoadStub", false, compilerClassLoader);
			return;
		} catch (ClassNotFoundException ignored) {
		}
		MethodHandles.Lookup l = UnsafeAccess.lookup();
		try {
			MethodHandle mh = l.findVirtual(Module.class, "implAddExports", MethodType.methodType(void.class, String.class));
			Module module = ModuleLayer.boot().findModule("jdk.compiler").orElseThrow(() -> new RuntimeException("Cannot find compiler module"));
			String[] packages = {
					"com.sun.tools.javac.code",
					"com.sun.tools.javac.comp",
					"com.sun.tools.javac.tree",
					"com.sun.tools.javac.util"
			};
			for (String pckg : packages) {
				mh.invokeExact(module, pckg);
			}
		} catch (OutOfMemoryError e) {
			throw e;
		} catch (Throwable t) {
			throw new RuntimeException("Cannot export compiler api packages", t);
		}
		Instrumentation instrumentation = InstrumentationProvider.get();
		ClassLoader pluginClassLoader = Initializer.class.getClassLoader();
		try {
			compilerClassLoader.loadClass("dev.xdark.betterrepeatable.java11.CompilerPatch");
		} catch (ClassNotFoundException ignored) {
			try (InputStream in = pluginClassLoader.getResourceAsStream("dev/xdark/betterrepeatable/java11/CompilerPatch.class")) {
				byte[] bytes = readStreamBytes(in);
				Method m = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
				m.setAccessible(true);
				m.invoke(compilerClassLoader, null, bytes, 0, bytes.length);
			} catch (ReflectiveOperationException | IOException ex) {
				throw new RuntimeException("Unable to load CompilerPatch", ex);
			}
		}
		ClassFileTransformer cf = new ClassFileTransformer() {
			@Override
			public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
				boolean annotate = "com/sun/tools/javac/comp/Annotate".equals(className);
				boolean check = "com/sun/tools/javac/comp/Check".equals(className);
				boolean visitor = "com/sun/tools/javac/comp/Annotate$AnnotationTypeVisitor".equals(className);
				if (annotate | check | visitor) {
					ClassReader reader = new ClassReader(classfileBuffer);
					ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES) {
						@Override
						protected ClassLoader getClassLoader() {
							return compilerClassLoader;
						}
					};
					if (annotate) {
						reader.accept(new ClassVisitor(ASM9, writer) {
							@Override
							public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
								MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
								if ("annotateNow".equals(name) && "(Lcom/sun/tools/javac/code/Symbol;Lcom/sun/tools/javac/util/List;Lcom/sun/tools/javac/comp/Env;ZZ)V".equals(descriptor)) {
									mv = new MethodVisitor(ASM9, mv) {

										@Override
										public void visitCode() {
											super.visitCode();
											visitVarInsn(ALOAD, 0);
											visitVarInsn(ALOAD, 1);
											visitVarInsn(ALOAD, 2);
											visitVarInsn(ALOAD, 3);
											visitVarInsn(ILOAD, 4);
											visitMethodInsn(INVOKESTATIC, "dev/xdark/betterrepeatable/java11/CompilerPatch", "annotateNow", "(Lcom/sun/tools/javac/comp/Annotate;Lcom/sun/tools/javac/code/Symbol;Lcom/sun/tools/javac/util/List;Lcom/sun/tools/javac/comp/Env;Z)Z", false);
											Label skip = new Label();
											visitJumpInsn(IFEQ, skip);
											visitInsn(RETURN);
											visitLabel(skip);
										}
									};
								}
								return mv;
							}
						}, 0);
					} else if (check) {
						reader.accept(new ClassVisitor(ASM9, writer) {
							@Override
							public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
								MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
								if ("validateRepeatable".equals(name) && "(Lcom/sun/tools/javac/code/Symbol$TypeSymbol;Lcom/sun/tools/javac/code/Attribute$Compound;Lcom/sun/tools/javac/util/JCDiagnostic$DiagnosticPosition;)V".equals(descriptor)) {
									mv = new MethodVisitor(ASM9, mv) {
										Label skip = new Label();

										@Override
										public void visitCode() {
											super.visitCode();
											visitVarInsn(ALOAD, 2);
											visitMethodInsn(INVOKESTATIC, "dev/xdark/betterrepeatable/java11/CompilerPatch", "isRepeatable", "(Lcom/sun/tools/javac/code/Attribute$Compound;)Z", false);
											visitJumpInsn(IFNE, skip);
										}

										@Override
										public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
											super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
											Label s = skip;
											if (s == null) {
												return;
											}
											if (opcode == INVOKESTATIC && "com/sun/tools/javac/util/Assert".equals(owner) && "check".equals(name) && "(Z)V".equals(descriptor)) {
												visitLabel(s);
												skip = null;
											}
										}
									};
								}
								return mv;
							}
						}, 0);
					} else {
						reader.accept(new ClassVisitor(ASM9, writer) {
							@Override
							public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
								MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
								if ("visitAnnotation".equals(name) && "(Lcom/sun/tools/javac/tree/JCTree$JCAnnotation;)V".equals(descriptor)) {
									mv = new MethodVisitor(ASM9, mv) {

										@Override
										public void visitInsn(int opcode) {
											if (opcode == RETURN) {
												visitVarInsn(ALOAD, 0);
												visitVarInsn(ALOAD, 1);
												visitMethodInsn(INVOKESTATIC, "dev/xdark/betterrepeatable/java11/CompilerPatch", "visitAnnotation", "(Lcom/sun/tools/javac/comp/Annotate$AnnotationTypeVisitor;Lcom/sun/tools/javac/tree/JCTree$JCAnnotation;)V", false);
											}
											super.visitInsn(opcode);
										}
									};
								}
								return mv;
							}
						}, 0);
					}
					return writer.toByteArray();
				}
				return null;
			}
		};
		instrumentation.addTransformer(cf, true);
		try {
			instrumentation.retransformClasses(Annotate.class);
			instrumentation.retransformClasses(Check.class);
			instrumentation.retransformClasses(Annotate.AnnotationTypeVisitor.class);
		} catch (UnmodifiableClassException e) {
			throw new IllegalStateException("Cannot patch classes", e);
		} catch (InternalError | VerifyError e) {
			throw new RuntimeException("Failed to retransform classes, CompilerPatch did not load?", e);
		} finally {
			instrumentation.removeTransformer(cf);
		}
		try (InputStream in = pluginClassLoader.getResourceAsStream("dev/xdark/betterrepeatable/java11/LoadStub.class")) {
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
