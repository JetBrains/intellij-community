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
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.Helper;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import org.jetbrains.annotations.NotNull;

public class PsiBasedFormattingModel implements FormattingModel {

  private final ASTNode myASTNode;
  private final Project myProject;
  private final FormattingDocumentModelImpl myDocumentModel;
  private final Block myRootBlock;

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.PsiBasedFormattingModel");
  private boolean myCanModifyAllWhiteSpaces = false;
  
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
        LOG.assertTrue(leafElement.getPsi().isValid());
        ASTNode prevNode = TreeUtil.prevLeaf(leafElement);

        if (prevNode != null) {
          IElementType type = prevNode.getElementType();
          if(type == ElementType.WHITE_SPACE) {
            final String s = prevNode.getText();

            if (s.indexOf("<![CDATA[") != -1) {
              return false;
            }

            prevNode = TreeUtil.prevLeaf(prevNode);
            type = prevNode != null ? prevNode.getElementType():null;
          }
          if(type == XmlElementType.XML_CDATA_END) return false;
        }
      }
      FormatterUtil.replaceWhiteSpace(whiteSpace, leafElement, ElementType.WHITE_SPACE, textRange);
      return true;
    } else if (textRange.getEndOffset() == myASTNode.getTextLength()){
      FormatterUtil.replaceLastWhiteSpace(myASTNode, whiteSpace, textRange);
      return true;
    } else {
      return false;
    }
  }

  private ASTNode findElementAt(final int offset) {
    PsiElement psiElement = InjectedLanguageUtil.findElementAt(myASTNode.getPsi().getContainingFile(), offset);
    if (psiElement == null) return null;
    if (psiElement instanceof PsiFile) {
      psiElement = myASTNode.getPsi().findElementAt(offset);
    }
    return psiElement.getNode();
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
}
