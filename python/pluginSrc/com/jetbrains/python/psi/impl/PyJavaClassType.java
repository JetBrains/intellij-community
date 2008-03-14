package com.jetbrains.python.psi.impl;

import com.intellij.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyResolveUtil;

/**
 * @author yole
 */
public class PyJavaClassType implements PyType {
  private PsiClass myClass;

  public PyJavaClassType(final PsiClass aClass) {
    myClass = aClass;
  }

  public PsiElement resolveMember(final String name) {
    final PsiMethod[] methods = myClass.findMethodsByName(name, true);
    if (methods.length > 0) {
      return methods [0]; // TODO[yole]: correct resolve
    }
    final PsiField field = myClass.findFieldByName(name, true);
    if (field != null) return field;
    return null;
  }

  public Object[] getCompletionVariants(final PyReferenceExpression referenceExpression) {
    final PyResolveUtil.VariantsProcessor processor = new PyResolveUtil.VariantsProcessor();
    myClass.processDeclarations(processor, ResolveState.initial(), null, referenceExpression);
    return processor.getResult();
  }
}
