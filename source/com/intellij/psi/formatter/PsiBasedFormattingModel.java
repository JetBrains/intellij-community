package com.intellij.psi.formatter;

import com.intellij.codeFormatting.general.FormatterUtil;
import com.intellij.lang.ASTNode;
import com.intellij.newCodeFormatting.Block;
import com.intellij.newCodeFormatting.FormattingDocumentModel;
import com.intellij.newCodeFormatting.FormattingModel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.codeStyle.Helper;
import com.intellij.psi.impl.source.jsp.jspJava.JspText;
import com.intellij.psi.impl.source.tree.ElementType;

import java.io.IOException;
import java.io.LineNumberReader;
import java.io.StringReader;

public class PsiBasedFormattingModel implements FormattingModel {
  
  private final ASTNode myASTNode;
  private final Project myProject;
  private final CodeStyleSettings mySettings;
  private final FormattingDocumentModelImpl myDocumentModel;
  private final Block myRootBlock;
  
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.PsiBasedFormattingModel");

  public PsiBasedFormattingModel(final PsiFile file,
                                 CodeStyleSettings settings, 
                                 final Block rootBlock) {
    mySettings = settings;
    myASTNode = SourceTreeToPsiMap.psiElementToTree(file);
    myProject = file.getProject();
    myDocumentModel = FormattingDocumentModelImpl.createOn(file);
    myRootBlock = rootBlock;
  }

  public int replaceWhiteSpace(TextRange textRange,
                               String whiteSpace,
                               final int blockLength){
    return replaceWithPSI(textRange, blockLength, whiteSpace);
  }

  private int replaceWithPSI(final TextRange textRange, int blockLength, final String whiteSpace) {
    final int offset = textRange.getEndOffset();
    final ASTNode leafElement = findElementAt(offset);

      if (leafElement != null) {
        LOG.assertTrue(leafElement.getPsi().isValid());
        final int oldElementLength = leafElement.getTextRange().getLength();
        if (false/*leafElement.getTextRange().getStartOffset() < textRange.getStartOffset()*/) {
          final int newElementLength = new Helper(StdFileTypes.JAVA, myProject).shiftIndentInside(leafElement, getSpaceCount(whiteSpace))
            .getTextRange().getLength();
          blockLength = blockLength - oldElementLength + newElementLength;
        }
        else {
          changeWhiteSpaceBeforeLeaf(whiteSpace, leafElement, textRange);
          if (leafElement.textContains('\n')
              && whiteSpace.indexOf('\n') >= 0) {
            try {
              Indent lastLineIndent = getLastLineIndent(leafElement.getText());
              Indent whiteSpaceIndent = createIndentOn(getLastLine(whiteSpace));
              final int shift = calcShift(lastLineIndent, whiteSpaceIndent);
              final int newElementLength = new Helper(StdFileTypes.JAVA, myProject).shiftIndentInside(leafElement, shift).getTextRange()
                .getLength();
              blockLength = blockLength - oldElementLength + newElementLength;
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        }

        return blockLength;
      } else {
        changeLastWhiteSpace(whiteSpace);
        return 0;
      }
    }

  protected void changeLastWhiteSpace(final String whiteSpace) {
    FormatterUtil.replaceLastWhiteSpace(myASTNode, whiteSpace);
  }

  protected void changeWhiteSpaceBeforeLeaf(final String whiteSpace, final ASTNode leafElement, final TextRange textRange) {
    FormatterUtil.replaceWhiteSpace(whiteSpace, leafElement, ElementType.WHITE_SPACE, textRange);
  }

  private int calcShift(final Indent lastLineIndent, final Indent whiteSpaceIndent) {
    final CodeStyleSettings.IndentOptions options = mySettings.JAVA_INDENT_OPTIONS;
    if (lastLineIndent.equals(whiteSpaceIndent)) return 0;
    if (options.USE_TAB_CHARACTER) {
      if (lastLineIndent.whiteSpaces > 0) {
        return whiteSpaceIndent.getSpacesCount(options);
      }
      else {
        return whiteSpaceIndent.tabs - lastLineIndent.tabs;
      }
    }
    else {
      if (lastLineIndent.tabs > 0) {
        return whiteSpaceIndent.getTabsCount(options);
      }
      else {
        return whiteSpaceIndent.whiteSpaces - lastLineIndent.whiteSpaces;
      }
    }
  }

  private int getSpaceCount(final String whiteSpace) {
    try {
      final String lastLine = getLastLine(whiteSpace);
      if (lastLine != null) {
        return lastLine.length();
      }
      else {
        return 0;
      }
    }
    catch (IOException e) {
      LOG.error(e);
      return 0;
    }
  }

  private ASTNode findElementAt(final int offset) {
    final PsiElement[] psiRoots = myASTNode.getPsi().getContainingFile().getPsiRoots();
    for (int i = psiRoots.length -1; i >= 0; i--) {
      PsiElement psiRoot = psiRoots[i];
      final ASTNode found = psiRoot.getNode().findLeafElementAt(offset);
      if (found != null) {
        if (!(found.getPsi()instanceof JspText) && found.getTextRange().getStartOffset() == offset) {
          return found;
        }
      }
    }
    return myASTNode.findLeafElementAt(offset);
  }

  private class Indent {
    public int whiteSpaces = 0;
    public int tabs = 0;

    public boolean equals(final Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      final Indent indent = (Indent)o;

      if (tabs != indent.tabs) return false;
      return whiteSpaces == indent.whiteSpaces;
    }

    public int hashCode() {
      int result;
      result = whiteSpaces;
      result = 29 * result + tabs;
      return result;
    }

    public int getTabsCount(final CodeStyleSettings.IndentOptions options) {
      final int tabsFromSpaces = whiteSpaces / options.TAB_SIZE;
      return tabs + tabsFromSpaces;
    }

    public int getSpacesCount(final CodeStyleSettings.IndentOptions options) {
      return whiteSpaces + tabs * options.TAB_SIZE;
    }
  }

  private Indent getLastLineIndent(final String text) throws IOException {
    String lastLine = getLastLine(text);
    if (lastLine == null) return new Indent();
    return createIndentOn(lastLine);
  }

  private Indent createIndentOn(final String lastLine) {
    final Indent result = new Indent();
    for (int i = 0; i < lastLine.length(); i++) {
      if (lastLine.charAt(i) == ' ') result.whiteSpaces += 1;
      if (lastLine.charAt(i) == '\t') result.tabs += 1;
    }
    return result;
  }

  private String getLastLine(final String text) throws IOException {
    if (text.endsWith("\n")) return "";
    final LineNumberReader lineNumberReader = new LineNumberReader(new StringReader(text));
    String line;
    String result = null;
    while ((line = lineNumberReader.readLine()) != null) {
      result = line;
    }
    return result;
  }

  public FormattingDocumentModel getDocumentModel() {
    return myDocumentModel;
  }

  public Block getRootBlock() {
    return myRootBlock;
  }
}
