package dev.xdark.betterrepeatable;

import com.sun.tools.attach.VirtualMachine;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.objectweb.asm.Opcodes.*;

public final class InstrumentationProvider {
	private static final String CLASS_PROPERTY = "dev.xdark.betterrepeatable.agentClass";
	private static final Object PROVIDER_LOCK = new Object();
	private static volatile Instrumentation instrumentation;

	private InstrumentationProvider() {
	}

	public static Instrumentation get() {
		Instrumentation instrumentation = InstrumentationProvider.instrumentation;
		if (instrumentation == null) {
			synchronized (PROVIDER_LOCK) {
				instrumentation = InstrumentationProvider.instrumentation;
				if (instrumentation == null) {
					instrumentation = loadAgent();
					InstrumentationProvider.instrumentation = instrumentation;
				}
			}
		}
		return instrumentation;
	}

	private static Instrumentation loadAgent() {
		// TODO Gradle is doing something wacky
		// java.lang.NoClassDefFoundError: org/gradle/internal/classpath/Instrumented
		String loadedAgentClass = invokeSystemMethod("getProperty", new Class[]{String.class}, CLASS_PROPERTY);
		if (loadedAgentClass != null) {
			try {
				Class<?> c = Class.forName(loadedAgentClass, false, ClassLoader.getSystemClassLoader());
				Field f = c.getDeclaredField("instance");
				f.setAccessible(true);
				Instrumentation instrumentation = (Instrumentation) f.get(null);
				if (instrumentation != null) {
					return instrumentation;
				}
			} catch (ReflectiveOperationException ignored) {
			}
			invokeSystemMethod("clearProperty", new Class[]{String.class}, CLASS_PROPERTY);
		}
		String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
		try {
			String normalName;
			VirtualMachine vm = null;
			try {
				vm = VirtualMachine.attach(pid);
				ClassWriter writer = new ClassWriter(0);
				String className = "dev/xdark/betterrepeatable/InstrumentationAgent" + System.currentTimeMillis();
				writer.visit(V1_8, ACC_PUBLIC | ACC_FINAL, className, null, "java/lang/Object", null);
				writer.visitField(ACC_PUBLIC | ACC_STATIC | ACC_VOLATILE, "instance", "Ljava/lang/instrument/Instrumentation;", null, null).visitEnd();
				MethodVisitor mv = writer.visitMethod(ACC_PUBLIC | ACC_STATIC | ACC_SYNCHRONIZED, "agentmain", "(Ljava/lang/String;Ljava/lang/instrument/Instrumentation;)V", null, null);
				mv.visitCode();
				mv.visitVarInsn(ALOAD, 1);
				mv.visitFieldInsn(PUTSTATIC, className, "instance", "Ljava/lang/instrument/Instrumentation;");
				mv.visitLdcInsn(Type.getObjectType(className));
				mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/Object", "notifyAll", "()V", false);
				mv.visitInsn(RETURN);
				mv.visitMaxs(1, 2);
				mv.visitEnd();
				writer.visitEnd();
				byte[] bytes = writer.toByteArray();
				Path agentJar = Files.createTempFile("better-repeatable-agent", ".jar");
				agentJar.toFile().deleteOnExit();
				Manifest manifest = new Manifest();
				Attributes mainAttributes = manifest.getMainAttributes();
				normalName = className.replace('/', '.');
				mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
				mainAttributes.put(new Attributes.Name("Agent-Class"), normalName);
				mainAttributes.put(new Attributes.Name("Can-Retransform-Classes"), "true");
				try (JarOutputStream jos = new JarOutputStream(Files.newOutputStream(agentJar))) {
					jos.putNextEntry(new JarEntry(className + ".class"));
					jos.write(bytes);
					jos.putNextEntry(new JarEntry(JarFile.MANIFEST_NAME));
					manifest.write(jos);
				}
				vm.loadAgent(agentJar.toString(), "");
			} finally {
				if (vm != null) {
					try {
						vm.detach();
					} catch (IOException ignored) {
					}
				}
			}
			Class<?> agentClass = Class.forName(normalName, true, ClassLoader.getSystemClassLoader());
			Field f = agentClass.getDeclaredField("instance");
			f.setAccessible(true);
			Instrumentation instrumentation;
			synchronized (agentClass) {
				instrumentation = (Instrumentation) f.get(null);
				if (instrumentation == null) {
					try {
						agentClass.wait();
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
						throw ex;
					}
					instrumentation = (Instrumentation) f.get(null);
				}
			}
			Objects.requireNonNull(instrumentation, "instrumentation");
			invokeSystemMethod("setProperty", new Class[]{String.class, String.class}, CLASS_PROPERTY, normalName);
			return instrumentation;
		} catch (Exception ex) {
			throw new RuntimeException("Failed to load agent", ex);
		}
	}

	private static <T> T invokeSystemMethod(String name, Class<?>[] types, Object... args) {
		try {
			return (T) System.class.getMethod(name, types).invoke(null, args);
		} catch (IllegalAccessException | NoSuchMethodException e) {
			throw new RuntimeException(e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e.getCause());
		}
	}
}
