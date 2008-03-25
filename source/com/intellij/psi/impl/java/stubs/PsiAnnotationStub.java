/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.stubs.StubElement;

public interface PsiAnnotationStub extends StubElement<PsiAnnotation> {
  String getText();
}