package com.intellij.psi.formatter;

import com.intellij.formatting.FormattingModel;
import com.intellij.formatting.Block;
import com.intellij.formatting.FormattingDocumentModel;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 * User: lesya
 * Date: Jul 10, 2005
 * Time: 9:16:23 PM
 * To change this template use File | Settings | File Templates.
 */
public class DocumentBasedFormattingModel implements FormattingModel {
  private final Block myRootBlock;
  private final FormattingDocumentModel myDocumentModel;
  private final Document myDocument;
  private final Project myProject;
  private final CodeStyleSettings mySettings;
  private final FileType myFileType;

  public DocumentBasedFormattingModel(final Block rootBlock,
                                      final Document document,
                                      final Project project,
                                      final CodeStyleSettings settings,
                                      final FileType fileType,
                                      final PsiFile file) {
    myRootBlock = rootBlock;
    myDocument = document;
    myProject = project;
    mySettings = settings;
    myFileType = fileType;
    myDocumentModel = new FormattingDocumentModelImpl(document,file);
  }

  @NotNull
  public Block getRootBlock() {
    return myRootBlock;
  }

  @NotNull
  public FormattingDocumentModel getDocumentModel() {
    return myDocumentModel;
  }

  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.formatter.DocumentBasedFormattingModel");
  public TextRange replaceWhiteSpace(TextRange textRange, String whiteSpace) {
    if (textRange.getLength() > 0) {
      final CharSequence current = myDocument.getCharsSequence().subSequence(textRange.getStartOffset(), textRange.getEndOffset());
      final String ws = current.toString();
      // whitespace element can contain non-white characters, e.g. in injected langs with CDATA
      //if (ws.trim().length() > 0) {
      //  LOG.assertTrue(false, "Document text:" + myDocument.getText() + "\nwsText:" + ws);
      //}

    }
    myDocument.replaceString(textRange.getStartOffset(),
                             textRange.getEndOffset(),
                             whiteSpace);

    return new TextRange(textRange.getStartOffset(), textRange.getStartOffset() + whiteSpace.length());
  }

  public TextRange shiftIndentInsideRange(TextRange range, int indent) {
    final int newLength = shiftIndentInside(range, indent);
    return new TextRange(range.getStartOffset(), range.getStartOffset() + newLength);
  }

  public void commitChanges() {
    PsiDocumentManager.getInstance(myProject).commitDocument(myDocument);
  }

  private int shiftIndentInside(final TextRange elementRange, final int shift) {
    final StringBuffer buffer = new StringBuffer();
    StringBuffer afterWhiteSpace = new StringBuffer();
    int whiteSpaceLength = 0;
    boolean insideWhiteSpace = true;
    int line = 0;
    for (int i = elementRange.getStartOffset(); i < elementRange.getEndOffset(); i++) {
      final char c = myDocument.getCharsSequence().charAt(i);
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
    myDocument.replaceString(elementRange.getStartOffset(), elementRange.getEndOffset(), buffer.toString());
    return buffer.length();
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
    return mySettings.getIndentOptions(myFileType);
  }

}
