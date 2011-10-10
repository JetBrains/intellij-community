package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import com.jetbrains.python.PyTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class PyCompletionConfidence extends CompletionConfidence {
  @NotNull
  @Override
  public ThreeState shouldFocusLookup(@NotNull CompletionParameters parameters) {
    return ThreeState.UNSURE;
  }

  @NotNull
  @Override
  public ThreeState shouldSkipAutopopup(@Nullable PsiElement contextElement, @NotNull PsiFile psiFile, int offset) {
    if (offset > 0) {
      PsiElement element = psiFile.findElementAt(offset-1);
      if (element != null) {
        ASTNode node = element.getNode();
        if (node != null && node.getElementType() == PyTokenTypes.FLOAT_LITERAL) {
          return ThreeState.YES;
        }
      }
    }
    return super.shouldSkipAutopopup(contextElement, psiFile, offset);
  }
}
