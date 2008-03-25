/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;

public class JavaMethodNameIndex extends StringStubIndexExtension<PsiMethod> {
  public static final StubIndexKey<String,PsiMethod> KEY = new StubIndexKey<String, PsiMethod>("java.method.name");

  public StubIndexKey<String, PsiMethod> getKey() {
    return KEY;
  }
}