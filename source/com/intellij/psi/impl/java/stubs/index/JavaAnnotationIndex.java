/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;

public class JavaAnnotationIndex extends StringStubIndexExtension<PsiAnnotation> {
  public static final StubIndexKey<String,PsiAnnotation> KEY = new StubIndexKey<String, PsiAnnotation>("java.annotations");

  public StubIndexKey<String, PsiAnnotation> getKey() {
    return KEY;
  }
}