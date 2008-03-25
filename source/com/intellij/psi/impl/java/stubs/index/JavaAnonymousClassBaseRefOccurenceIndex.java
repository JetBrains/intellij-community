/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.index;

import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;

public class JavaAnonymousClassBaseRefOccurenceIndex extends StringStubIndexExtension<PsiAnonymousClass> {
  public static final StubIndexKey<String,PsiAnonymousClass> KEY = new StubIndexKey<String, PsiAnonymousClass>("java.anonymous.baseref");

  public StubIndexKey<String, PsiAnonymousClass> getKey() {
    return KEY;
  }
}