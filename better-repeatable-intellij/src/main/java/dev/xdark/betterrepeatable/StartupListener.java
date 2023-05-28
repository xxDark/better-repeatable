package dev.xdark.betterrepeatable;

import com.intellij.codeInsight.daemon.impl.analysis.AnnotationsHighlightUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import sun.misc.Unsafe;

import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.security.ProtectionDomain;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.objectweb.asm.Opcodes.*;

public final class StartupListener implements StartupActivity {
	private static final AtomicBoolean INITIALIZED = new AtomicBoolean();

	@Override
	public void runActivity(@NotNull Project project) {
		if (INITIALIZED.compareAndSet(false, true)) {
			Instrumentation instrumentation = InstrumentationProvider.get();
			try {
				byte[] classBytes;
				try (InputStream in = getClass().getClassLoader().getResourceAsStream("dev/xdark/betterrepeatable/IntellijRedirect.class")) {
					classBytes = in.readAllBytes();
				}
				Field field = Unsafe.class.getDeclaredField("theUnsafe");
				field.setAccessible(true);
				Unsafe unsafe = (Unsafe) field.get(null);
				field = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
				MethodHandles.publicLookup();
				MethodHandles.Lookup lookup = (MethodHandles.Lookup) unsafe.getObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field));
				MethodHandle mh = lookup.findVirtual(ClassLoader.class, "defineClass", MethodType.methodType(Class.class, String.class, byte[].class, int.class, int.class));
				//noinspection unused
				Class<?> unused = (Class<?>) mh.invokeExact(AnnotationsHighlightUtil.class.getClassLoader(), (String) null, classBytes, 0, classBytes.length);
			} catch (OutOfMemoryError e) {
				throw e;
			} catch (Throwable t) {
				throw new RuntimeException("Cannot load IntellijRedirect", t);
			}
			ClassFileTransformer transformer = new ClassFileTransformer() {
				@Override
				public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) throws IllegalClassFormatException {
					if ("com/intellij/codeInsight/daemon/impl/analysis/AnnotationsHighlightUtil".equals(className)) {
						try {
							ClassReader reader = new ClassReader(classfileBuffer);
							ClassWriter writer = new ClassWriter(reader, 0);
							reader.accept(new ClassVisitor(ASM9, writer) {
								@Override
								public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
									MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
									if ("checkDuplicateAnnotations".equals(name) && "(Lcom/intellij/psi/PsiAnnotation;Lcom/intellij/pom/java/LanguageLevel;)Lcom/intellij/codeInsight/daemon/impl/HighlightInfo;".equals(descriptor)) {
										mv = new MethodVisitor(ASM9, mv) {
											@Override
											public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
												if ("com/intellij/psi/impl/PsiImplUtil".equals(owner) && "findAnnotation".equals(name) && "(Lcom/intellij/psi/PsiAnnotationOwner;Ljava/lang/String;)Lcom/intellij/psi/PsiAnnotation;".equals(descriptor)) {
													owner = "dev/xdark/betterrepeatable/IntellijRedirect";
												}
												super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
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
			instrumentation.addTransformer(transformer, true);
			try {
				instrumentation.retransformClasses(AnnotationsHighlightUtil.class);
			} catch (UnmodifiableClassException ex) {
				throw new RuntimeException("Cannot modify AnnotationsHighlightUtil class", ex);
			}
		}
	}
}
