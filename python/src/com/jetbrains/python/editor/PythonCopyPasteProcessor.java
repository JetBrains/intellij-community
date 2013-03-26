package com.jetbrains.python.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.CopyPastePreProcessor;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.CharFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * User : catherine
 */
public class PythonCopyPasteProcessor implements CopyPastePreProcessor {

  @Nullable
  @Override
  public String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
    return null;
  }

  @Override
  public String preprocessOnPaste(Project project,
                                  PsiFile file,
                                  Editor editor,
                                  String text,
                                  RawText rawText) {
    if (!CodeInsightSettings.getInstance().INDENT_TO_CARET_ON_PASTE) {
      return text;
    }
    final boolean useTabs =
      CodeStyleSettingsManager.getSettings(project).useTabCharacter(PythonFileType.INSTANCE);
    CharFilter NOT_INDENT_FILTER = new CharFilter() {
      public boolean accept(char ch) {
        return useTabs? ch != '\t' : ch != ' ';
      }
    };

    final CaretModel caretModel = editor.getCaretModel();
    final SelectionModel selectionModel = editor.getSelectionModel();
    final Document document = editor.getDocument();
    final int caretOffset = selectionModel.getSelectionStart() != selectionModel.getSelectionEnd() ?
                            selectionModel.getSelectionStart() : caretModel.getOffset();
    final int lineNumber = document.getLineNumber(caretOffset);
    final int lineStartOffset = getLineStartSafeOffset(document, lineNumber);

    final String indentText = getIndentText(file, document, caretOffset, lineNumber);

    int toRemove = calculateIndentToRemove(text, NOT_INDENT_FILTER);
    if (StringUtil.isEmptyOrSpaces(indentText) && isApplicable(file, text, caretOffset)) {
      caretModel.moveToOffset(lineStartOffset);
      editor.getSelectionModel().setSelection(lineStartOffset, selectionModel.getSelectionEnd());
    }

    final List<String> strings = StringUtil.split(text, "\n", false);
    String newText = "";
    if (StringUtil.isEmptyOrSpaces(indentText)) {
      for (String s : strings) {
        newText += indentText + StringUtil.trimStart(s, StringUtil.repeat(useTabs? "\t" : " ", toRemove));
      }
    }
    else {
      newText = text;
    }

    String toString = document.getText(TextRange.create(lineStartOffset, document.getLineEndOffset(lineNumber)));
    if (addLinebreak(text, toString, useTabs) && selectionModel.getSelectionStart() == selectionModel.getSelectionEnd())
      newText += "\n";
    return newText;
  }

  private static String getIndentText(@NotNull final PsiFile file,
                                      @NotNull final Document document,
                                      int caretOffset,
                                      int lineNumber) {

    PsiElement nonWS = PyUtil.findNextAtOffset(file, caretOffset, PsiWhiteSpace.class);
    if (nonWS != null) {
      final IElementType nonWSType = nonWS.getNode().getElementType();
      if (nonWSType == PyTokenTypes.ELSE_KEYWORD || nonWSType == PyTokenTypes.ELIF_KEYWORD ||
          nonWSType == PyTokenTypes.EXCEPT_KEYWORD || nonWSType == PyTokenTypes.FINALLY_KEYWORD) {
        lineNumber -= 1;
        nonWS = PyUtil.findNextAtOffset(file, getLineStartSafeOffset(document, lineNumber), PsiWhiteSpace.class);
      }
    }

    int lineStartOffset = getLineStartSafeOffset(document, lineNumber);
    String indentText = document.getText(TextRange.create(lineStartOffset, caretOffset));

    if (nonWS != null && document.getLineNumber(nonWS.getTextOffset()) == lineNumber) {
      indentText = document.getText(TextRange.create(lineStartOffset, nonWS.getTextOffset()));
    }
    else if (caretOffset == lineStartOffset) {
      final PsiElement ws = file.findElementAt(lineStartOffset);
      if (ws != null) {
        final String wsText = ws.getText();
        final List<String> strings = StringUtil.split(wsText, "\n");
        if (strings.size() >= 1) {
          indentText = strings.get(0);
        }
      }
    }
    return indentText;
  }

  private static int calculateIndentToRemove(@NotNull String text, @NotNull final CharFilter filter) {
    final List<String> strings = StringUtil.split(text, "\n", false);
    int minIndent = StringUtil.findFirst(text, filter);
    for (String  s : strings) {
      final int indent = StringUtil.findFirst(s, filter);
      if (indent < minIndent)
        minIndent = indent;
    }
    return minIndent;
  }

  private static boolean isApplicable(@NotNull final PsiFile file, @NotNull String text, int caretOffset) {
    final boolean useTabs =
      CodeStyleSettingsManager.getSettings(file.getProject()).useTabCharacter(PythonFileType.INSTANCE);
    final PsiElement nonWS = PyUtil.findNextAtOffset(file, caretOffset, PsiWhiteSpace.class);
    if (nonWS == null || text.endsWith("\n"))
      return true;
    if (inStatementList(file, caretOffset) && (text.startsWith(useTabs ? "\t" : " ") || StringUtil.split(text, "\n").size() > 1))
      return true;
    return false;
  }

  private static boolean inStatementList(@NotNull final PsiFile file, int caretOffset) {
    final PsiElement element = file.findElementAt(caretOffset);
    final PsiElement element1 = file.findElementAt(caretOffset);
    return PsiTreeUtil.getParentOfType(element, PyStatementList.class) != null ||
           PsiTreeUtil.getParentOfType(element1, PyStatementList.class) != null ||
           PsiTreeUtil.getParentOfType(element, PyFunction.class) != null ||
           PsiTreeUtil.getParentOfType(element1, PyFunction.class) != null ||
           PsiTreeUtil.getParentOfType(element, PyClass.class) != null ||
           PsiTreeUtil.getParentOfType(element1, PyClass.class) != null;
  }

  private static boolean addLinebreak(@NotNull String text, @NotNull String toString, boolean useTabs) {
    if ((text.startsWith(useTabs ? "\t" : " ") || StringUtil.split(text, "\n").size() > 1)
        && !text.endsWith("\n") && !StringUtil.isEmptyOrSpaces(toString))
      return true;
    return false;
  }

  public static int getLineStartSafeOffset(final Document document, int line) {
    if (line == document.getLineCount()) return document.getTextLength();
    if (line < 0) return 0;
    return document.getLineStartOffset(line);
  }

}
