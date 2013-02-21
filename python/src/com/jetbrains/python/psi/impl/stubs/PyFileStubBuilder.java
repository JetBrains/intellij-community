package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.DefaultStubBuilder;
import com.intellij.psi.stubs.StubElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class PyFileStubBuilder extends DefaultStubBuilder {
  @Override
  protected StubElement createStubForFile(@NotNull PsiFile file) {
    if (file instanceof PyFile) {
      return new PyFileStubImpl((PyFile)file);
    }

    return super.createStubForFile(file);
  }

  @Override
  protected boolean skipChildProcessingWhenBuildingStubs(@NotNull PsiElement parent, @NotNull PsiElement element) {
    return parent instanceof PyIfStatement && PyUtil.isIfNameEqualsMain((PyIfStatement)parent);
  }

  @Override
  public boolean skipChildProcessingWhenBuildingStubs(@NotNull ASTNode parent, @NotNull ASTNode node) {
    PsiElement psi = parent.getPsi();
    return psi instanceof PyIfStatement && PyUtil.isIfNameEqualsMain((PyIfStatement)psi);
  }
}
