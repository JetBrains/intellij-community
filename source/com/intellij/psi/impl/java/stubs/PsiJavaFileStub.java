/*
 * @author max
 */
package com.intellij.psi.impl.java.stubs;

import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.stubs.PsiFileStub;

public interface PsiJavaFileStub extends PsiFileStub<PsiJavaFile> {
  String getPackageName();
}