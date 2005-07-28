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
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.Helper;
import com.intellij.psi.impl.source.jsp.jspJava.JspText;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;

public class PsiBasedFormattingModel implements FormattingModel {

  private final ASTNode myASTNode;
  private final Project myProject;
  private final FormattingDocumentModelImpl myDocumentModel;
  private final Block myRootBlock;

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.PsiBasedFormattingModel");


  public PsiBasedFormattingModel(final PsiFile file,
                                 final Block rootBlock,
                                 final FormattingDocumentModelImpl documentModel) {
    myASTNode = SourceTreeToPsiMap.psiElementToTree(file);
    myProject = file.getProject();
    myDocumentModel = documentModel;
    myRootBlock = rootBlock;

  }

  public TextRange replaceWhiteSpace(TextRange textRange,
                                String whiteSpace) {
    if (replaceWithPSI(textRange, whiteSpace)){
      return new TextRange(textRange.getStartOffset(), textRange.getStartOffset() + whiteSpace.length());
    } else {
      return textRange;
    }
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

    if (leafElement != null && leafElement.getTextRange().equals(textRange) && Helper.mayShiftIndentInside(leafElement)) {
      return new Helper(StdFileTypes.JAVA, myProject).shiftIndentInside(leafElement, shift).getTextRange();
    } else {
      return textRange;
    }

  }

  private boolean replaceWithPSI(final TextRange textRange, final String whiteSpace) {
    final int offset = textRange.getEndOffset();
    final ASTNode leafElement = findElementAt(offset);
      if (leafElement != null) {
        if (leafElement.getElementType() == ElementType.WHITE_SPACE) return false;
        if (isNonEmptyWhiteSpace(leafElement)) return false;
        LOG.assertTrue(leafElement.getPsi().isValid());
        ASTNode prevNode = TreeUtil.prevLeaf(leafElement);
        if (prevNode != null && prevNode.getElementType() == ElementType.XML_CDATA_END) return false;
        if (isNonEmptyWhiteSpace(prevNode)) {
          return false;
        }
        changeWhiteSpaceBeforeLeaf(whiteSpace, leafElement, textRange);
        return true;
      } else if (textRange.getEndOffset() == myASTNode.getTextLength()){
        changeLastWhiteSpace(whiteSpace);
        return true;
      } else {
        return false;
      }
    }

  private boolean isNonEmptyWhiteSpace(ASTNode prevNode) {
    return prevNode != null && prevNode.getElementType() == ElementType.WHITE_SPACE && prevNode.getText().trim().length()  >0;
  }

  protected void changeLastWhiteSpace(final String whiteSpace) {
    FormatterUtil.replaceLastWhiteSpace(myASTNode, whiteSpace);
  }

  protected void changeWhiteSpaceBeforeLeaf(final String whiteSpace, final ASTNode leafElement, final TextRange textRange) {
    FormatterUtil.replaceWhiteSpace(whiteSpace, leafElement, ElementType.WHITE_SPACE, textRange);
  }



  private ASTNode findElementAt(final int offset) {
    final PsiElement[] psiRoots = myASTNode.getPsi().getContainingFile().getPsiRoots();
    for (int i = psiRoots.length -1; i >= 0; i--) {
      PsiElement psiRoot = psiRoots[i];
      final ASTNode found = psiRoot.getNode().findLeafElementAt(offset);
      if (found != null) {
        if (found.getElementType() == ElementType.WHITE_SPACE) return found;
        if (!(found.getPsi()instanceof JspText) && found.getTextRange().getStartOffset() == offset) {
          if (found.getElementType() == ElementType.XML_COMMENT_START) {
            return found.getTreeParent();
          } else {
            return found;
          }
        }
      }
    }
    final ASTNode found = myASTNode.findLeafElementAt(offset);
    if (found != null && found.getElementType() == ElementType.XML_COMMENT_START) {
      return found.getTreeParent();
    } else {
      return found;
    }
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
