/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.editor;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.editorActions.CopyPastePreProcessor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.CharFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
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
    if (!CodeInsightSettings.getInstance().INDENT_TO_CARET_ON_PASTE || file.getLanguage() != PythonLanguage.getInstance()) {
      return text;
    }
    final CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(project);
    final boolean useTabs = codeStyleSettings.useTabCharacter(PythonFileType.INSTANCE);
    final int indentSize = codeStyleSettings.getIndentSize(PythonFileType.INSTANCE);
    CharFilter NOT_INDENT_FILTER = new CharFilter() {
      public boolean accept(char ch) {
        return useTabs? ch != '\t' : ch != ' ';
      }
    };
    final String indentChar = useTabs ? "\t" : " ";

    final CaretModel caretModel = editor.getCaretModel();
    final SelectionModel selectionModel = editor.getSelectionModel();
    final Document document = editor.getDocument();
    final int caretOffset = selectionModel.getSelectionStart() != selectionModel.getSelectionEnd() ?
                            selectionModel.getSelectionStart() : caretModel.getOffset();
    final int lineNumber = document.getLineNumber(caretOffset);
    final int lineStartOffset = getLineStartSafeOffset(document, lineNumber);
    final int lineEndOffset = document.getLineEndOffset(lineNumber);

    final PsiElement element = file.findElementAt(caretOffset);
    if (PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class) != null) return text;

    text = addLeadingSpaces(text, NOT_INDENT_FILTER, indentSize, indentChar);
    int firstLineIndent = StringUtil.findFirst(text, NOT_INDENT_FILTER);
    final String indentText = getIndentText(file, document, caretOffset, lineNumber, firstLineIndent);

    int toRemove = calculateIndentToRemove(text, NOT_INDENT_FILTER);

    final String toString = document.getText(TextRange.create(lineStartOffset, lineEndOffset));
    if (StringUtil.isEmptyOrSpaces(indentText) && isApplicable(file, text, caretOffset)) {
      caretModel.moveToOffset(lineStartOffset);

      if (StringUtil.isEmptyOrSpaces(toString)) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            document.deleteString(lineStartOffset, lineEndOffset);
          }
        });
      }
      editor.getSelectionModel().setSelection(lineStartOffset, selectionModel.getSelectionEnd());
    }

    final List<String> strings = StringUtil.split(text, "\n", false);
    String newText = "";
    if (StringUtil.isEmptyOrSpaces(indentText)) {
      for (String s : strings) {
        newText += indentText + StringUtil.trimStart(s, StringUtil.repeat(indentChar, toRemove));
      }
    }
    else {
      newText = text;
    }

    if (addLinebreak(text, toString, useTabs) && selectionModel.getSelectionStart() == selectionModel.getSelectionEnd())
      newText += "\n";
    return newText;
  }

  private static String addLeadingSpaces(String text, final CharFilter filter, int indentSize, String indentChar) {
    final List<String> strings = StringUtil.split(text, "\n", false);
    if (strings.size() > 1) {
      int firstLineIndent = StringUtil.findFirst(strings.get(0), filter);
      int secondLineIndent = StringUtil.findFirst(strings.get(1), filter);
      final int diff = secondLineIndent - firstLineIndent;
      if (diff > indentSize) {
        text = StringUtil.repeat(indentChar, diff - indentSize) + text;
      }
    }
    return text;
  }

  private static String getIndentText(@NotNull final PsiFile file,
                                      @NotNull final Document document,
                                      int caretOffset,
                                      int lineNumber, int firstLineIndent) {

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
      if (indentText.length() == firstLineIndent)
        return "";
    }
    return indentText;
  }

  private static int calculateIndentToRemove(@NotNull String text, @NotNull final CharFilter filter) {
    final List<String> strings = StringUtil.split(text, "\n", false);
    int minIndent = StringUtil.findFirst(text, filter);
    for (String  s : strings) {
      final int indent = StringUtil.findFirst(s, filter);
      if (indent < minIndent && !StringUtil.isEmptyOrSpaces(s))
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
    return PsiTreeUtil.getParentOfType(element, PyStatementList.class) != null ||
           PsiTreeUtil.getParentOfType(element, PyFunction.class) != null ||
           PsiTreeUtil.getParentOfType(element, PyClass.class) != null;
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
