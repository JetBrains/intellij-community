package com.jetbrains.python.psi.impl.stubs;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.DefaultStubBuilder;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyUtil;

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

  @Override
  protected boolean skipChildProcessingWhenBuildingStubs(PsiElement element, PsiElement child) {
    if (element instanceof PyIfStatement) {
      return PyUtil.isIfNameEqualsMain((PyIfStatement)element);
    }
    return false;
  }
}
