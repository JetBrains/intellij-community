/*
 * @author max
 */
package com.intellij.psi.impl.source;

import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.java.stubs.impl.PsiJavaFileStubImpl;
import com.intellij.psi.stubs.DefaultStubBuilder;
import com.intellij.psi.stubs.StubElement;

public class JavaFileStubBuilder extends DefaultStubBuilder {
  protected StubElement createStubForFile(final PsiFile file) {
    return new PsiJavaFileStubImpl((PsiJavaFile)file, false);
  }
}