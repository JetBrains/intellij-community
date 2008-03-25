/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.psi.PsiClass;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;

public class JavaShortClassNameIndex extends StringStubIndexExtension<PsiClass> {
  public static final StubIndexKey<String,PsiClass> KEY = new StubIndexKey<String, PsiClass>("java.class.shortname");

  public StubIndexKey<String, PsiClass> getKey() {
    return KEY;
  }
}