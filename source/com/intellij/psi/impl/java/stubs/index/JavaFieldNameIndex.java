/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.psi.PsiField;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;

public class JavaFieldNameIndex extends StringStubIndexExtension<PsiField> {
  public static final StubIndexKey<String,PsiField> KEY = new StubIndexKey<String, PsiField>("java.field.name");

  public StubIndexKey<String, PsiField> getKey() {
    return KEY;
  }
}