package com.intellij.psi.formatter;

import com.intellij.codeFormatting.general.FormatterUtil;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingDocumentModel;
import com.intellij.formatting.FormattingModel;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.codeStyle.Helper;
import com.intellij.psi.impl.source.tree.ElementType;
import org.jetbrains.annotations.NotNull;

public final class PsiBasedFormattingModel implements FormattingModel {

  private final ASTNode myTreeRoot;
  private final Project myProject;
  private final FormattingDocumentModel myDocumentModel;
  private final Block myRootBlock;

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.PsiBasedFormattingModel");


  public PsiBasedFormattingModel(final PsiFile file,
                                 final Block rootBlock,
                                 final FormattingDocumentModel documentModel) {
    myTreeRoot = file.getPsiRoots()[0].getNode();
    myProject = file.getProject();
    myDocumentModel = documentModel;
    myRootBlock = rootBlock;
  }

  public PsiBasedFormattingModel(ASTNode myTreeRoot, Project myProject, FormattingDocumentModel myDocumentModel, Block myRootBlock) {
    this.myTreeRoot = myTreeRoot;
    this.myProject = myProject;
    this.myDocumentModel = myDocumentModel;
    this.myRootBlock = myRootBlock;
  }

  public void replaceWhiteSpace(TextRange textRange,
                                String whiteSpace) {
    replaceWithPSI(textRange, whiteSpace);
  }

  public TextRange shiftIndentInsideRange(TextRange textRange, int shift) {
    return shiftIndentInsideWithPsi(textRange, shift);
  }

  public void commitChanges() {
  }


  private TextRange shiftIndentInsideWithPsi(final TextRange textRange, final int shift) {
    final int offset = textRange.getStartOffset();
    ASTNode leafElement = myTreeRoot.findLeafElementAt(offset);
    while (!leafElement.getTextRange().equals(textRange)) {
      leafElement = leafElement.getTreeParent();
      if (leafElement == null) return textRange;
    }
    if (leafElement.getTextRange().equals(textRange) && Helper.mayShiftIndentInside(leafElement)) {
      return new Helper(StdFileTypes.JAVA, myProject).shiftIndentInside(leafElement, shift).getTextRange();
    } else {
      return textRange;
    }
  }

  private void replaceWithPSI(final TextRange textRange, final String whiteSpace) {
    ASTNode elementContainingRangeStart = findElementContainingRangeStart(textRange);
    if (canReplaceWhiteSpaceInsideLeaf(elementContainingRangeStart, textRange)) {
      FormatterUtil.replaceTokenText(elementContainingRangeStart, whiteSpace, textRange);
    } else {
      ASTNode elementContainingRangeEnd = myTreeRoot.findLeafElementAt(textRange.getEndOffset());
      if (elementContainingRangeEnd == null) {
        changeLastWhiteSpace(whiteSpace);
      }
      else if (canReplaceWhiteSpaceInsideLeaf(elementContainingRangeEnd, textRange)) {
        FormatterUtil.replaceTokenText(elementContainingRangeEnd, whiteSpace, textRange);
      }
      else if (elementContainingRangeEnd.getTextRange().getStartOffset() == textRange.getEndOffset()) {
        LOG.assertTrue(elementContainingRangeEnd.getPsi().isValid());
        changeWhiteSpaceBeforeLeaf(whiteSpace, elementContainingRangeEnd, textRange);
      }
      else {
        LOG.assertTrue(false, myTreeRoot.getText());
      }
    }
  }

  private boolean canReplaceWhiteSpaceInsideLeaf(ASTNode wsElement, TextRange textRange) {
    if (wsElement == null) return false;
    if (wsElement.getElementType() == ElementType.WHITE_SPACE) return false;
    if (wsElement.getElementType() == ElementType.DOC_COMMENT_DATA && wsElement.getText().trim().length() == 0) return false;
    if (FormatterUtil.canInsertWhiteSpaceInto(wsElement)) return true;
    if (containsRange(wsElement, textRange, false)) return true;
    if (textRange.getLength() > 0) return containsRange(wsElement, textRange, true);





    return false;
  }

  private ASTNode findElementContainingRangeStart(TextRange textRange) {
    if (textRange.getLength() == 0) {
      if (textRange.getStartOffset() == 0) {
        return null;
      } else {
        return myTreeRoot.findLeafElementAt(textRange.getStartOffset() - 1);
      }
    } else {
      return myTreeRoot.findLeafElementAt(textRange.getStartOffset());
    }

  }

  private boolean containsRange(ASTNode found, TextRange textRange, boolean withEnds) {
    TextRange foundRange = found.getTextRange();
    if (withEnds) {
      return foundRange.getStartOffset() <= textRange.getStartOffset() && foundRange.getEndOffset() >= textRange.getEndOffset();
    } else {
      return foundRange.getStartOffset() < textRange.getStartOffset() && foundRange.getEndOffset() > textRange.getEndOffset();
    }

  }

  protected void changeLastWhiteSpace(final String whiteSpace) {
    FormatterUtil.replaceLastWhiteSpace(myTreeRoot, whiteSpace);
  }

  protected void changeWhiteSpaceBeforeLeaf(final String whiteSpace, final ASTNode leafElement, final TextRange textRange) {
    FormatterUtil.replaceWhiteSpace(whiteSpace, leafElement, ElementType.WHITE_SPACE, textRange);
  }


  @NotNull
  public FormattingDocumentModel getDocumentModel() {
    return myDocumentModel;
  }

  @NotNull
  public Block getRootBlock() {
    return myRootBlock;
  }
}
