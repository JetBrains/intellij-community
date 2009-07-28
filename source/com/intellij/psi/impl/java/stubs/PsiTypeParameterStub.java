/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.stubs.NamedStub;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface PsiTypeParameterStub extends NamedStub<PsiTypeParameter> {
  @NotNull
  List<PsiAnnotationStub> getAnnotations();
}