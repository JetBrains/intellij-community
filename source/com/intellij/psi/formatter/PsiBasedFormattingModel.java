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
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.jsp.JspElementType;
import com.intellij.psi.xml.XmlElementType;
import org.jetbrains.annotations.NotNull;

public class PsiBasedFormattingModel implements FormattingModel {

  private final ASTNode myASTNode;
  private final Project myProject;
  private final FormattingDocumentModelImpl myDocumentModel;
  private final Block myRootBlock;

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.PsiBasedFormattingModel");
  private boolean myCanModifyAllWhiteSpaces = false;

  private boolean myUseAllTrees = true;
  
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
        if (!myCanModifyAllWhiteSpaces) {
          if (leafElement.getElementType() == ElementType.WHITE_SPACE) return false;
          if (isNonEmptyWhiteSpace(leafElement)) return false;
          LOG.assertTrue(leafElement.getPsi().isValid());
          ASTNode prevNode = TreeUtil.prevLeaf(leafElement);
          if (prevNode != null && prevNode.getElementType() == XmlElementType.XML_CDATA_END) return false;
          if (isNonEmptyWhiteSpace(prevNode)) {
            return false;
          }
        }
        changeWhiteSpaceBeforeLeaf(whiteSpace, leafElement, textRange);
        return true;
      } else if (textRange.getEndOffset() == myASTNode.getTextLength()){
        changeLastWhiteSpace(whiteSpace, textRange);
        return true;
      } else {
        return false;
      }
    }

  private boolean isNonEmptyWhiteSpace(ASTNode prevNode) {
    return prevNode != null && prevNode.getElementType() == ElementType.WHITE_SPACE && prevNode.getText().trim().length()  >0;
  }

  protected void changeLastWhiteSpace(final String whiteSpace, final TextRange textRange) {
    FormatterUtil.replaceLastWhiteSpace(myASTNode, whiteSpace, textRange);
  }

  protected void changeWhiteSpaceBeforeLeaf(final String whiteSpace, final ASTNode leafElement, final TextRange textRange) {
    FormatterUtil.replaceWhiteSpace(whiteSpace, leafElement, ElementType.WHITE_SPACE, textRange);
  }



  private ASTNode findElementAt(final int offset) {
    if (myUseAllTrees) {
      final PsiElement psiElement = myASTNode.getPsi().findElementAt(offset);
      if (psiElement == null) return null;
      /*
      if (psiElement.getTextRange().getStartOffset() != offset) {
        return null;
      }
      */
      return psiElement.getNode();
    }
    else {
      final ASTNode result = myASTNode.findLeafElementAt(offset);
      if (result == null) return null;
      if (result.getTextRange().getStartOffset() != offset && result.getElementType() == JspElementType.HOLDER_TEMPLATE_DATA) {
        return null;
      }      
      return result;
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

  public void canModifyAllWhiteSpaces() {
    myCanModifyAllWhiteSpaces = true;
  }
  
  public void doNotUseallTrees(){
    myUseAllTrees = false;
  }
}
