/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiClass;
import com.intellij.psi.stubs.NamedStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public interface PsiClassStub extends NamedStub<PsiClass> {
  @NonNls
  @Nullable
  String getQualifiedName();

  @NonNls 
  @Nullable
  String getBaseClassReferenceText();

  boolean isDeprecated();
  boolean isInterface();
  boolean isEnum();
  boolean isEnumConstantInitializer();
  boolean isAnonymous();
  boolean isAnnotationType();
}