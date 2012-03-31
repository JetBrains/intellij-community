package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.CompletionConfidence;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
        if (node != null) {
          if (node.getElementType() == PyTokenTypes.FLOAT_LITERAL) {
            return ThreeState.YES;
          }
          if (PyTokenTypes.STRING_NODES.contains(node.getElementType())) {
            final PsiElement parent = element.getParent();
            if (parent instanceof PyStringLiteralExpression) {
              final List<TextRange> ranges = ((PyStringLiteralExpression)parent).getStringValueTextRanges();
              int relativeOffset = offset - parent.getTextRange().getStartOffset();
              if (ranges.size() > 0 && relativeOffset < ranges.get(0).getStartOffset()) {
                return ThreeState.YES;
              }
            }
          }
        }
      }
    }
    return super.shouldSkipAutopopup(contextElement, psiFile, offset);
  }
}
