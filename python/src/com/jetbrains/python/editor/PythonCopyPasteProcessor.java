/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.psi.PyUtil.as;

/**
 * User : catherine
 */
public class PythonCopyPasteProcessor implements CopyPastePreProcessor {

  @Nullable
  @Override
  public String preprocessOnCopy(PsiFile file, int[] startOffsets, int[] endOffsets, String text) {
    return null;
  }

  @NotNull
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
        return ch != (useTabs ? '\t' : ' ');
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

    final String linePrefix = document.getText(TextRange.create(lineStartOffset, caretOffset));
    if (!StringUtil.isEmptyOrSpaces(linePrefix)) return text;

    final PsiElement element = file.findElementAt(caretOffset);
    if (PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class) != null) return text;

    text = addLeadingSpaces(text, NOT_INDENT_FILTER, indentSize, indentChar);
    final String indentText = getIndentText(file, document, caretOffset, lineNumber);

    final String line = document.getText(TextRange.create(lineStartOffset, lineEndOffset));
    if (StringUtil.isEmptyOrSpaces(indentText) && shouldPasteOnPreviousLine(file, text, caretOffset)) {
      caretModel.moveToOffset(lineStartOffset);
      editor.getSelectionModel().setSelection(lineStartOffset, selectionModel.getSelectionEnd());

      if (StringUtil.isEmptyOrSpaces(line)) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            document.deleteString(lineStartOffset, lineEndOffset);
          }
        });
      }
    }

    String newText;
    if (StringUtil.isEmptyOrSpaces(indentText)) {
      newText = PyIndentUtil.changeIndent(text, false, indentText);
    }
    else {
      newText = text;
    }

    if (addLinebreak(text, line, useTabs) && selectionModel.getSelectionStart() == selectionModel.getSelectionEnd()) {
      newText += "\n";
    }
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

    if (nonWS != null && document.getLineNumber(nonWS.getTextOffset()) == lineNumber) {
      return PyIndentUtil.getLineIndent(document, lineNumber);
    }

    int lineStartOffset = getLineStartSafeOffset(document, lineNumber);
    final PsiElement ws = file.findElementAt(lineStartOffset);
    final String userIndent = document.getText(TextRange.create(lineStartOffset, caretOffset));
    if (ws != null) {
      PyStatementList statementList = findEmptyStatementListNearby(ws);
      if (statementList != null) {
        return PyIndentUtil.getElementIndent(statementList);
      }

      final String smallestIndent = PyIndentUtil.getElementIndent(ws);
      final PyStatementListContainer parentBlock = PsiTreeUtil.getParentOfType(ws, PyStatementListContainer.class);
      final PyStatementListContainer deepestBlock = getDeepestPossibleParentBlock(ws);
      final String greatestIndent;
      if (deepestBlock != null && (parentBlock == null || PsiTreeUtil.isAncestor(parentBlock, deepestBlock, true))) {
        greatestIndent = PyIndentUtil.getElementIndent(deepestBlock.getStatementList());
      }
      else {
        greatestIndent = smallestIndent;
      }
      if (smallestIndent.startsWith(userIndent)) {
        return smallestIndent;
      }
      if (userIndent.startsWith(greatestIndent)) {
        return greatestIndent;
      }
    }
    return userIndent;
  }

  @Nullable
  private static PyStatementList findEmptyStatementListNearby(@NotNull PsiElement whitespace) {
    PyStatementList statementList = ObjectUtils.chooseNotNull(as(whitespace.getNextSibling(), PyStatementList.class),
                                                              as(whitespace.getPrevSibling(), PyStatementList.class));
    if (statementList == null) {
      final PsiElement prevLeaf = getPrevNonCommentLeaf(whitespace);
      if (prevLeaf instanceof PsiErrorElement) {
        statementList = as(prevLeaf.getParent(), PyStatementList.class);
      }
    }
    return statementList != null && statementList.getStatements().length == 0 ? statementList : null;
  }

  @Nullable
  private static PyStatementListContainer getDeepestPossibleParentBlock(@NotNull PsiElement whitespace) {
    final PsiElement prevLeaf = getPrevNonCommentLeaf(whitespace);
    return PsiTreeUtil.getParentOfType(prevLeaf, PyStatementListContainer.class);
  }

  private static boolean shouldPasteOnPreviousLine(@NotNull final PsiFile file, @NotNull String text, int caretOffset) {
    final boolean useTabs = CodeStyleSettingsManager.getSettings(file.getProject()).useTabCharacter(PythonFileType.INSTANCE);
    final PsiElement nonWS = PyUtil.findNextAtOffset(file, caretOffset, PsiWhiteSpace.class);
    if (nonWS == null || text.endsWith("\n")) {
      return true;
    }
    if (inStatementList(file, caretOffset) && (text.startsWith(useTabs ? "\t" : " ") || StringUtil.split(text, "\n").size() > 1)) {
      return true;
    }
    return false;
  }

  @Nullable
  private static PsiElement getPrevNonCommentLeaf(@NotNull PsiElement element) {
    PsiElement anchor = PsiTreeUtil.prevLeaf(element);
    while (anchor instanceof PsiComment || anchor instanceof PsiWhiteSpace) {
      anchor = PsiTreeUtil.prevLeaf(anchor, false);
    }
    return anchor;
  }

  private static boolean inStatementList(@NotNull final PsiFile file, int caretOffset) {
    final PsiElement element = file.findElementAt(caretOffset);
    return PsiTreeUtil.getParentOfType(element, PyStatementListContainer.class) != null;
  }

  private static boolean addLinebreak(@NotNull String text, @NotNull String line, boolean useTabs) {
    return (text.startsWith(useTabs ? "\t" : " ") || StringUtil.split(text, "\n").size() > 1)
           && !text.endsWith("\n") && !StringUtil.isEmptyOrSpaces(line);
  }

  private static int getLineStartSafeOffset(final Document document, int line) {
    if (line >= document.getLineCount()) return document.getTextLength();
    if (line < 0) return 0;
    return document.getLineStartOffset(line);
  }

}
