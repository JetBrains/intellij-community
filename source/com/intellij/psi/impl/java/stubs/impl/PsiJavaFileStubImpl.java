/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs.impl;

import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.java.stubs.PsiJavaFileStub;
import com.intellij.psi.stubs.PsiFileStubImpl;

public class PsiJavaFileStubImpl extends PsiFileStubImpl<PsiJavaFile> implements PsiJavaFileStub {
  private String myPackageName;

  public PsiJavaFileStubImpl(final PsiJavaFile file) {
    super(file);
  }

  public PsiJavaFileStubImpl(final String packageName) {
    super(null);
    myPackageName = packageName;
  }

  public String getPackageName() {
    return myPackageName;
  }
}