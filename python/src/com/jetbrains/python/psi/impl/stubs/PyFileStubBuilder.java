package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.DefaultStubBuilder;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyFile;

/**
 * @author yole
 */
public class PyFileStubBuilder extends DefaultStubBuilder {
  @Override
  protected StubElement createStubForFile(PsiFile file) {
    if (file instanceof PyFile) {
      return new PyFileStubImpl((PyFile)file);
    }

    return super.createStubForFile(file);
  }
}
