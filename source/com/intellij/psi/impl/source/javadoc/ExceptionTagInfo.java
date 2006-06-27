package com.intellij.psi.impl.source.javadoc;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NonNls;

/**
 * @author mike
 */
class ExceptionTagInfo implements JavadocTagInfo {
  private String myName;

  public ExceptionTagInfo(@NonNls String name) {
    myName = name;
  }

  public String checkTagValue(PsiDocTagValue value) {
    if (value == null) return JavaErrorMessages.message("javadoc.exception.tag.exception.class.expected");
    final PsiElement firstChild = value.getFirstChild();
    if (firstChild == null) return JavaErrorMessages.message("javadoc.exception.tag.exception.class.expected");

    final PsiElement psiElement = firstChild.getFirstChild();
    if (!(psiElement instanceof PsiJavaCodeReferenceElement)) {
      return JavaErrorMessages.message("javadoc.exception.tag.wrong.tag.value");
    }

    final PsiJavaCodeReferenceElement ref = ((PsiJavaCodeReferenceElement)psiElement);
    final PsiElement element = ref.resolve();
    if (!(element instanceof PsiClass)) return null;

    final PsiClass exceptionClass = (PsiClass)element;


    final PsiClass throwable = value.getManager().findClass("java.lang.Throwable", value.getResolveScope());

    if (throwable != null) {
      if (!exceptionClass.equals(throwable) && !exceptionClass.isInheritor(throwable, true)) {
        return JavaErrorMessages.message("javadoc.exception.tag.class.is.not.throwable", exceptionClass.getQualifiedName());
      }
    }

    final PsiClass runtimeException = value.getManager().findClass("java.lang.RuntimeException", value.getResolveScope());

    if (runtimeException != null &&
        (exceptionClass.isInheritor(runtimeException, true) || exceptionClass.equals(runtimeException))) {
      return null;
    }

    final PsiClass errorException = value.getManager().findClass("java.lang.Error", value.getResolveScope());

    if (errorException != null &&
        (exceptionClass.isInheritor(errorException, true) || exceptionClass.equals(errorException))) {
      return null;
    }

    PsiMethod method = PsiTreeUtil.getParentOfType(value, PsiMethod.class);
    if (method == null) {
      return null;
    }
    final PsiClassType[] references = method.getThrowsList().getReferencedTypes();

    for (PsiClassType reference : references) {
      final PsiClass psiClass = reference.resolve();
      if (psiClass == null) continue;
      if (exceptionClass.isInheritor(psiClass, true) || exceptionClass.equals(psiClass)) return null;
    }

    return JavaErrorMessages.message("javadoc.exception.tag.exception.is.not.thrown", exceptionClass.getName(), method.getName());
  }

  public String getName() {
    return myName;
  }

  public Object[] getPossibleValues(PsiElement context, PsiElement place, String prefix) {
    return ArrayUtil.EMPTY_OBJECT_ARRAY;
  }

  public PsiReference getReference(PsiDocTagValue value) {
    return null;
  }

  public boolean isValidInContext(PsiElement element) {
    if (!(element instanceof PsiMethod)) return false;
    return true;
  }

  public boolean isInline() {
    return false;
  }
}
