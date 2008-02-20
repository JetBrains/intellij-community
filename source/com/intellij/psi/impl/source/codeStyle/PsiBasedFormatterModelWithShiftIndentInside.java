/*
 * @author max
 */
package com.intellij.psi.impl.source.codeStyle;

import com.intellij.formatting.Block;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.formatter.FormattingDocumentModelImpl;
import com.intellij.psi.formatter.PsiBasedFormattingModel;

public class PsiBasedFormatterModelWithShiftIndentInside extends PsiBasedFormattingModel {
  private Project myProject;

  public PsiBasedFormatterModelWithShiftIndentInside(final PsiFile file,
                                                     final Block rootBlock,
                                                     final FormattingDocumentModelImpl documentModel) {
    super(file, rootBlock, documentModel);
    myProject = file.getProject();
  }

  public TextRange shiftIndentInsideRange(TextRange textRange, int shift) {
    return shiftIndentInsideWithPsi(textRange, shift);
  }

  public void commitChanges() {
  }


  private TextRange shiftIndentInsideWithPsi(final TextRange textRange, final int shift) {
    final int offset = textRange.getStartOffset();

    ASTNode leafElement = findElementAt(offset);
    while (leafElement != null && !leafElement.getTextRange().equals(textRange)) {
      leafElement = leafElement.getTreeParent();
    }

    if (leafElement != null && leafElement.getTextRange().equals(textRange) && ShiftIndentInsideHelper.mayShiftIndentInside(leafElement)) {
      return new ShiftIndentInsideHelper(StdFileTypes.JAVA, myProject).shiftIndentInside(leafElement, shift).getTextRange();
    } else {
      return textRange;
    }

  }

}