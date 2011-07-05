package com.jetbrains.python.psi.impl.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.stubs.DefaultStubBuilder;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.tree.IElementType;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.Nullable;

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
    return element instanceof PyIfStatement && PyUtil.isIfNameEqualsMain((PyIfStatement)element);
  }

  @Override
  public boolean skipChildProcessingWhenBuildingStubs(@Nullable ASTNode parent, IElementType childType) {
    if (parent != null) {
      final PsiElement psi = parent.getPsi();
      if (psi != null) {
        return psi instanceof PyIfStatement && PyUtil.isIfNameEqualsMain((PyIfStatement)psi);
      }
    }
    return super.skipChildProcessingWhenBuildingStubs(parent, childType);
  }
}
