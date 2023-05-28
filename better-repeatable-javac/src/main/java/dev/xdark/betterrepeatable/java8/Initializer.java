package dev.xdark.betterrepeatable.java8;

import com.sun.tools.attach.AgentInitializationException;
import com.sun.tools.attach.AgentLoadException;
import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.javac.code.SymbolMetadata;
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
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.ProtectionDomain;

import static org.objectweb.asm.Opcodes.*;

public final class Initializer {

	public static void init() {
		ClassLoader scl = ClassLoader.getSystemClassLoader();
		Class<?> agentClass;
		try {
			Method m = ClassLoader.class.getDeclaredMethod("findLoadedClass", String.class);
			m.setAccessible(true);
			agentClass = (Class<?>) m.invoke(scl, "dev.xdark.betterrepeatable.InstrumentationAgent");
		} catch (ReflectiveOperationException ex) {
			agentClass = null;
		}
		if (agentClass != null) {
			return;
		}
		String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
		VirtualMachine vm;
		try {
			vm = VirtualMachine.attach(pid);
		} catch (AttachNotSupportedException e) {
			throw new RuntimeException("Failed to attach to VM", e);
		} catch (IOException e) {
			throw new RuntimeException("I/O error", e);
		}
		try {
			vm.loadAgent(Paths.get(Initializer.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString(), "");
		} catch (AgentLoadException e) {
			throw new RuntimeException("Failed to load agent", e);
		} catch (IOException | URISyntaxException e) {
			throw new RuntimeException("I/O error", e);
		} catch (AgentInitializationException e) {
			throw new RuntimeException("Failed to initialize agent", e);
		} finally {
			try {
				vm.detach();
			} catch (IOException ignored) {
			}
		}
		try {
			agentClass = Class.forName("dev.xdark.betterrepeatable.InstrumentationAgent", false, scl);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Failed to load agent class", e);
		}
		Object lock;
		{
			try {
				Field lockField = agentClass.getDeclaredField("LOCK");
				lockField.setAccessible(true);
				lock = lockField.get(null);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException("Failed to get agent lock field", e);
			}
		}

		Instrumentation instrumentation;
		try {
			Field f = agentClass.getDeclaredField("instrumentation");
			f.setAccessible(true);
			Instrumentation probe;
			synchronized (lock) {
				probe = (Instrumentation) f.get(null);
				if (probe == null) {
					try {
						lock.wait();
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new IllegalStateException("Thread interrupted");
					}
					probe = (Instrumentation) f.get(null);
				}
			}
			instrumentation = probe;
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException("Failed to get instrumentation", e);
		}
		ClassLoader compilerClassLoader = SymbolMetadata.class.getClassLoader();
		try (InputStream in = Initializer.class.getClassLoader().getResourceAsStream("dev/xdark/betterrepeatable/java8/SymbolMetadataPatch.class")) {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buf = new byte[8192];
			int r;
			while ((r = in.read(buf)) != -1) {
				baos.write(buf, 0, r);
			}
			byte[] bytes = baos.toByteArray();
			Method m = ClassLoader.class.getDeclaredMethod("defineClass", String.class, byte[].class, int.class, int.class);
			m.setAccessible(true);
			m.invoke(compilerClassLoader, null, bytes, 0, bytes.length);
		} catch (ReflectiveOperationException | IOException ex) {
			throw new RuntimeException("Unable to load SymbolMetadataPatch", ex);
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
											visitVarInsn(ALOAD, 1);
											visitMethodInsn(INVOKESTATIC, "dev/xdark/betterrepeatable/java8/SymbolMetadataPatch", "getAttributesForCompletion", "(Lcom/sun/tools/javac/comp/Annotate$AnnotateRepeatedContext;)Lcom/sun/tools/javac/util/List;", false);
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
		} catch (InternalError e) {
			throw new RuntimeException("Failed to retransform SymbolMetadata, SymbolMetadataPatch did not load?", e);
		}
	}
}
