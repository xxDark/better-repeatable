package dev.xdark.betterrepeatable.java11;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symtab;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.comp.AttrContext;
import com.sun.tools.javac.comp.Env;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Assert;
import com.sun.tools.javac.util.List;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

public final class CompilerPatch extends JCTree.Visitor {
	private static final String ANNOTATION_NAME = "dev.xdark.betterrepeatable.Repeatable";
	private static final Unsafe UNSAFE;
	private static final long Annotate_syms;
	private static final long Visitor_repeatable;
	private static final long Visitor_annotate;
	private static final long Visitor_env;

	private CompilerPatch() {
	}

	public static synchronized <T extends Attribute.Compound> boolean annotateNow(
			Annotate annotate,
			Symbol toAnnotate,
			List<JCTree.JCAnnotation> withAnnotations,
			Env<AttrContext> env,
			boolean typeAnnotations
	) {
		if (typeAnnotations) return false;
		boolean noRepeatable = true;
		Symtab symtab = (Symtab) UNSAFE.getObject(annotate, Annotate_syms);
		loop:
		for (List<JCTree.JCAnnotation> al = withAnnotations; !al.isEmpty(); al = al.tail) {
			JCTree.JCAnnotation a = al.head;
			T c = (T) annotate.attributeAnnotation(a, symtab.annotationType, env);
			if (c == null) {
				continue;
			}
			Symbol.TypeSymbol typeSymbol = c.type.tsym;
			Annotate.AnnotationTypeMetadata metadata = typeSymbol.getAnnotationTypeMetadata();
			metadata.complete();
			Attribute.Compound repeatable = metadata.getRepeatable();
			if (repeatable != null && repeatable.type.tsym.getQualifiedName().contentEquals(ANNOTATION_NAME)) {
				noRepeatable = false;
				break;
			}
		}
		if (noRepeatable) {
			return false;
		}
		List<T> buf = List.nil();
		for (List<JCTree.JCAnnotation> al = withAnnotations; !al.isEmpty(); al = al.tail) {
			JCTree.JCAnnotation a = al.head;
			T c = (T) annotate.attributeAnnotation(a, symtab.annotationType, env);
			Assert.checkNonNull(c, "Failed to create annotation");
			buf = buf.prepend(c);
		}
		buf = buf.reverse();
		toAnnotate.resetAnnotations();
		toAnnotate.setDeclarationAttributes((List<Attribute.Compound>) buf);
		return true;
	}

	public static boolean isRepeatable(Attribute.Compound repeatable) {
		return repeatable.type.tsym.getQualifiedName().contentEquals(ANNOTATION_NAME);
	}

	public static void visitAnnotation(Annotate.AnnotationTypeVisitor visitor, JCTree.JCAnnotation annotation) {
		Type type = annotation.annotationType.type;

		if (type != null && type.tsym.getQualifiedName().contentEquals(ANNOTATION_NAME)) {
			Unsafe u = UNSAFE;
			u.putObject(visitor, Visitor_repeatable, ((Annotate) u.getObject(visitor, Visitor_annotate)).attributeAnnotation(annotation, type, (Env<AttrContext>) u.getObject(visitor, Visitor_env)));
		}
	}

	static {
		try {
			Field f = Unsafe.class.getDeclaredField("theUnsafe");
			f.setAccessible(true);
			Unsafe u = (Unsafe) f.get(null);
			UNSAFE = u;
			f = Annotate.class.getDeclaredField("syms");
			Annotate_syms = u.objectFieldOffset(f);
			Visitor_repeatable = u.objectFieldOffset(Annotate.AnnotationTypeVisitor.class.getDeclaredField("repeatable"));
			long offset = -1L;
			for (Field candidate : Annotate.AnnotationTypeVisitor.class.getDeclaredFields()) {
				if (candidate.getType() == Annotate.class) {
					offset = u.objectFieldOffset(candidate);
					break;
				}
			}
			if (offset == -1L) {
				throw new RuntimeException("No Annotate in AnnotationTypeVisitor");
			}
			Visitor_annotate = offset;
			Visitor_env = u.objectFieldOffset(Annotate.AnnotationTypeVisitor.class.getDeclaredField("env"));
		} catch (ReflectiveOperationException e) {
			throw new ExceptionInInitializerError(e);
		}
	}
}
