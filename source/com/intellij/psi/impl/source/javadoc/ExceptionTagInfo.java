package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.*;
import com.intellij.psi.javadoc.JavadocTagInfo;
import com.intellij.psi.javadoc.PsiDocTagValue;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ArrayUtil;

/**
 * @author mike
 */
class ExceptionTagInfo implements JavadocTagInfo {
  private String myName;

  public ExceptionTagInfo(String name) {
    myName = name;
  }

  public String checkTagValue(PsiDocTagValue value) {
    if (value == null) return "Exception class expected";

    if (!(value.getFirstChild() instanceof PsiJavaCodeReferenceElement)) return "Wrong tag value";

    final PsiJavaCodeReferenceElement ref = ((PsiJavaCodeReferenceElement)value.getFirstChild());
    final PsiClass exceptionClass = (PsiClass)ref.resolve();
    if (exceptionClass == null) return null;

    final PsiClass throwable = value.getManager().findClass("java.lang.Throwable", value.getResolveScope());

    if (throwable != null) {
      if (!exceptionClass.equals(throwable) && !exceptionClass.isInheritor(throwable, true)) {
        return "Class " + exceptionClass.getQualifiedName() + " is not a descendant of Throwable";
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
    final PsiClassType[] references = method.getThrowsList().getReferencedTypes();

    for (int i = 0; i < references.length; i++) {
      final PsiClass psiClass = references[i].resolve();
      if (psiClass == null) continue;
      if (exceptionClass.isInheritor(psiClass, true) || exceptionClass.equals(psiClass)) return null;
    }

    return exceptionClass.getName() + " is not declared to be thrown by method " + method.getName();
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
