package dev.xdark.betterrepeatable.java8;

import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.comp.Annotate;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

public final class SymbolMetadataPatch {
	private static final String ANNOTATION_NAME = "dev.xdark.betterrepeatable.Repeatable";

	private SymbolMetadataPatch() {
	}

	// Copy from SymbolMetadata#getAttributesForCompletion
	public static <T extends Attribute.Compound> List<T> getAttributesForCompletion(Annotate.AnnotateRepeatedContext<T> ctx) {
		List<T> buf = List.nil();
		java.util.List<Map.Entry<T, JCDiagnostic.DiagnosticPosition>> entries = ctx.pos.entrySet()
				.stream()
				.sorted(Comparator.comparingInt(x -> x.getValue().getPreferredPosition()))
				.collect(Collectors.toList());
		boolean noRepeatable = true;
		loop:
		for (Map.Entry<Symbol.TypeSymbol, ListBuffer<T>> entry : ctx.annotated.entrySet()) {
			Symbol.TypeSymbol sym = entry.getKey();
			for (Attribute.Compound rawAttribute : sym.getRawAttributes()) {
				if (rawAttribute.type.tsym.getQualifiedName().contentEquals(ANNOTATION_NAME)) {
					noRepeatable = false;
					break loop;
				}
			}
		}
		if (noRepeatable) {
			return null;
		}
		// TODO preserve original behaviour for annotations
		// with java.lang.annotation variant?
		for (Map.Entry<T, JCDiagnostic.DiagnosticPosition> entry : entries) {
			T key = entry.getKey();
			buf = buf.prepend(key);
		}
		return buf.reverse();
	}
}