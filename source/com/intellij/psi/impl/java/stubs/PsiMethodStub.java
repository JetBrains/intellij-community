/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.impl.cache.TypeInfo;
import com.intellij.psi.stubs.NamedStub;

public interface PsiMethodStub extends NamedStub<PsiMethod> {
  boolean isConstructor();
  boolean isVarArgs();
  boolean isAnnotationMethod();

  String getDefaultValueText();
  TypeInfo getReturnTypeText();

  boolean isDeprecated();
  boolean hasDeprecatedAnnotation();

  PsiParameterStub findParameter(int idx);
}