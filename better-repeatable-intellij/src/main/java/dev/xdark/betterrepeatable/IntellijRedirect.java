package dev.xdark.betterrepeatable;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.impl.PsiImplUtil;

public final class IntellijRedirect {

	private IntellijRedirect() {
	}

	public static PsiAnnotation findAnnotation(PsiAnnotationOwner owner, String name) {
		PsiAnnotation annotation = PsiImplUtil.findAnnotation(owner, name);
		if (annotation == null) {
			annotation = PsiImplUtil.findAnnotation(owner, "dev.xdark.betterrepeatable.Repeatable");
		}
		return annotation;
	}
}
