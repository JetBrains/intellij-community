/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.psi.PsiModifierList;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;

public class JavaSuperClassNameOccurenceIndex extends StringStubIndexExtension<PsiModifierList> {
  public static final StubIndexKey<String,PsiModifierList> KEY = new StubIndexKey<String, PsiModifierList>("java.class.extlist");

  public StubIndexKey<String, PsiModifierList> getKey() {
    return KEY;
  }
}