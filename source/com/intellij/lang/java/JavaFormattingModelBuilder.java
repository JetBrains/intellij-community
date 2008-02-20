/*
 * @author max
 */
package com.intellij.lang.java;

import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.FormattingModelBuilder;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.java.AbstractJavaBlock;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.PsiBasedFormatterModelWithShiftIndentInside;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaFormattingModelBuilder implements FormattingModelBuilder {
  @NotNull
    public FormattingModel createModel(final PsiElement element, final CodeStyleSettings settings) {
    final FileElement fileElement = TreeUtil.getFileElement((TreeElement)SourceTreeToPsiMap.psiElementToTree(element));
    return new PsiBasedFormatterModelWithShiftIndentInside (element.getContainingFile(), AbstractJavaBlock.createJavaBlock(fileElement,
                                                                                                                           settings),
                                                            FormattingDocumentModelImpl.createOn(element.getContainingFile()));
  }

  public TextRange getRangeAffectingIndent(final PsiFile file, final int offset, final ASTNode elementAtOffset) {
    ASTNode current = elementAtOffset;
    current = findNearestExpressionParent(current);
    if (current == null) {
      if (elementAtOffset.getElementType() == TokenType.WHITE_SPACE) {
        ASTNode prevElement = elementAtOffset.getTreePrev();
        if (prevElement == null) {
          return elementAtOffset.getTextRange();
        }
        else {
          ASTNode prevExpressionParent = findNearestExpressionParent(prevElement);
          if (prevExpressionParent == null) {
            return elementAtOffset.getTextRange();
          }
          else {
            return new TextRange(prevExpressionParent.getTextRange().getStartOffset(), elementAtOffset.getTextRange().getEndOffset());
          }
        }
      }
      else {
        return elementAtOffset.getTextRange();
      }

    }
    else {
      return current.getTextRange();
    }
  }

  @Nullable
  private static ASTNode findNearestExpressionParent(final ASTNode current) {
    ASTNode result = current;
    while (result != null) {
      PsiElement psi = ((TreeElement)result).getTransformedFirstOrSelf().getPsi();
      if (psi instanceof PsiExpression && !(psi.getParent() instanceof PsiExpression)) {
        return result;
      }
      result = result.getTreeParent();
    }
    return result;
  }
}