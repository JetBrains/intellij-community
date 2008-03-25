/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.psi.PsiClass;
import com.intellij.psi.stubs.IntStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;

public class JavaFullClassNameIndex extends IntStubIndexExtension<PsiClass> {
  public static final StubIndexKey<Integer,PsiClass> KEY = new StubIndexKey<Integer, PsiClass>("java.class.fqn");

  public StubIndexKey<Integer, PsiClass> getKey() {
    return KEY;
  }
}