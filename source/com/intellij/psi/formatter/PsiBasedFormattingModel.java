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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.Helper;
import com.intellij.psi.impl.source.jsp.jspJava.JspText;
import com.intellij.psi.impl.source.tree.ElementType;
import org.jetbrains.annotations.NotNull;

public class PsiBasedFormattingModel implements FormattingModel {

  private final ASTNode myASTNode;
  private final Project myProject;
  private final FormattingDocumentModelImpl myDocumentModel;
  private final Block myRootBlock;

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.PsiBasedFormattingModel");
  private final CodeStyleSettings mySettings;

  public PsiBasedFormattingModel(final PsiFile file,
                                 final Block rootBlock, final CodeStyleSettings settings) {
    mySettings = settings;
    myASTNode = SourceTreeToPsiMap.psiElementToTree(file);
    myProject = file.getProject();
    myDocumentModel = FormattingDocumentModelImpl.createOn(file);
    myRootBlock = rootBlock;

  }

  public void replaceWhiteSpace(TextRange textRange,
                                String whiteSpace) {
    if (myDocumentModel.useDocument()) {
      replaceWithDocument(textRange, whiteSpace);
    } else {
      replaceWithPSI(textRange, whiteSpace);
    }
  }

  private void replaceWithDocument(final TextRange textRange, final String whiteSpace) {
    myDocumentModel.getDocument().replaceString(textRange.getStartOffset(),
                                                textRange.getEndOffset(),
                                                whiteSpace);
  }

  public TextRange shiftIndentInsideRange(TextRange textRange, int shift) {
    if (myDocumentModel.useDocument()) {
      return shiftIndentInsideWithDocument(textRange,shift);
    } else {
      return shiftIndentInsideWithPsi(textRange, shift);
    }
  }

  private TextRange shiftIndentInsideWithDocument(final TextRange textRange, final int shift) {
    final int newLength = shiftIndentInside(textRange, shift);
    return new TextRange(textRange.getStartOffset(), newLength);
  }

  private int shiftIndentInside(final TextRange elementRange, final int shift) {
    final StringBuffer buffer = new StringBuffer();
    StringBuffer afterWhiteSpace = new StringBuffer();
    int whiteSpaceLength = 0;
    boolean insideWhiteSpace = true;
    int line = 0;
    for (int i = elementRange.getStartOffset(); i < elementRange.getEndOffset(); i++) {
      final char c = myDocumentModel.getDocument().getCharsSequence().charAt(i);
      switch (c) {
        case '\n':
          if (line > 0) {
            createWhiteSpace(whiteSpaceLength + shift, buffer);
          }
          buffer.append(afterWhiteSpace.toString());
          insideWhiteSpace = true;
          whiteSpaceLength = 0;
          afterWhiteSpace = new StringBuffer();
          buffer.append(c);
          line++;
          break;
        case ' ':
          if (insideWhiteSpace) {
            whiteSpaceLength += 1;
          }
          else {
            afterWhiteSpace.append(c);
          }
          break;
        case '\t':
          if (insideWhiteSpace) {
            whiteSpaceLength += getIndentOptions().TAB_SIZE;
          }
          else {
            afterWhiteSpace.append(c);
          }

          break;
        default:
          insideWhiteSpace = false;
          afterWhiteSpace.append(c);
      }
    }
    if (line > 0) {
      createWhiteSpace(whiteSpaceLength + shift, buffer);
    }
    buffer.append(afterWhiteSpace.toString());
    myDocumentModel.getDocument().replaceString(elementRange.getStartOffset(), elementRange.getEndOffset(), buffer.toString());
    return buffer.length() - elementRange.getLength();
  }

  private void createWhiteSpace(final int whiteSpaceLength, StringBuffer buffer) {
    final CodeStyleSettings.IndentOptions indentOptions = getIndentOptions();
    if (indentOptions.USE_TAB_CHARACTER) {
      int tabs = whiteSpaceLength / indentOptions.TAB_SIZE;
      int spaces = whiteSpaceLength - tabs * indentOptions.TAB_SIZE;
      StringUtil.repeatSymbol(buffer, '\t', tabs);
      StringUtil.repeatSymbol(buffer, ' ', spaces);
    }
    else {
      StringUtil.repeatSymbol(buffer, ' ', whiteSpaceLength);
    }
  }

  private CodeStyleSettings.IndentOptions getIndentOptions() {
    return mySettings.getIndentOptions(myASTNode.getPsi().getContainingFile().getFileType());
  }

  private TextRange shiftIndentInsideWithPsi(final TextRange textRange, final int shift) {
    final int offset = textRange.getStartOffset();
    final ASTNode leafElement = findElementAt(offset);
    return new Helper(StdFileTypes.JAVA, myProject).shiftIndentInside(leafElement, shift).getTextRange();
  }

  private void replaceWithPSI(final TextRange textRange, final String whiteSpace) {
    final int offset = textRange.getEndOffset();
    final ASTNode leafElement = findElementAt(offset);
      if (leafElement != null) {
        LOG.assertTrue(leafElement.getPsi().isValid());
        changeWhiteSpaceBeforeLeaf(whiteSpace, leafElement, textRange);
      } else {
        changeLastWhiteSpace(whiteSpace);
      }
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
