/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.console;

import com.google.common.base.CharMatcher;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.DocumentEx;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.codeStyle.IndentHelperImpl;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NotNull;

/**
 * Created by Yuli Fiterman on 9/16/2016.
 */
public class ConsoleEnterHandler {
  //REturns true if compelte
  public boolean handleEnterPressed(EditorEx editor) {
    EditorActionHandler enterHandler = EditorActionManager.getInstance().getActionHandler(IdeActions.ACTION_EDITOR_ENTER);
    Project project = editor.getProject();
    assert project != null;


    if (editor.getDocument().getLineCount() != 0) {
      editor.getSelectionModel().removeSelection();
      LogicalPosition caretPosition = editor.getCaretModel().getLogicalPosition();
      int lineEndOffset = editor.getDocument().getLineEndOffset(caretPosition.line);
      editor.getCaretModel().moveToOffset(lineEndOffset);
    }


    DocumentEx document = editor.getDocument();
    Caret currentCaret = editor.getCaretModel().getCurrentCaret();
    String prevLine = getLineAtOffset(document, currentCaret.getOffset());

    new WriteCommandAction(project) {

      @Override
      protected void run(@NotNull Result result) throws Throwable {
        enterHandler.execute(editor, currentCaret, DataManager.getInstance().getDataContext(editor.getComponent()));
      }
    }.execute();

    if (prevLine.startsWith("%%")) { //IPython Magics!
      return false;
    }

    PsiDocumentManager psiMgr = PsiDocumentManager.getInstance(project);
    PsiFile psiFile = psiMgr.getPsiFile(document);
    assert psiFile != null;
    int caretOffset = currentCaret.getOffset();
    PsiElement currentElement = psiFile.findElementAt(caretOffset);

    if (currentElement != null && (PyTokenTypes.TRIPLE_NODES.contains(currentElement.getNode().getElementType()) ||
                                   currentElement.getNode().getElementType() == PyTokenTypes.DOCSTRING)) {
      return false;
    }


    String textAfterCursor = document.getText(new TextRange(caretOffset, document.getTextLength()));
     /* Check if we're on the last line, if not then don't want to execute */
    if (!StringUtil.isEmptyOrSpaces(textAfterCursor)) {
      return false;
    }

    if (CharMatcher.WHITESPACE.matchesAllOf(prevLine)) {
      return true;
    }

    /* don't execute if previous line has an indent */
    int prevIndent = IndentHelperImpl.getIndent(project, PythonFileType.INSTANCE, prevLine, false);
    if (prevIndent > 0) {
      return false;
    }

     /* If we have an indent we don't want to execute either */
    String currentLine = getLineAtOffset(document, caretOffset);
    int indent = IndentHelperImpl.getIndent(project, PythonFileType.INSTANCE, currentLine, false);
    if (indent > 0) {
      return false;
    }



    return currentElement == null || currentElement instanceof PsiWhiteSpace;
  }

  @NotNull
  public static String getLineAtOffset(@NotNull Document doc, int offset) {
    int line = doc.getLineNumber(offset);
    int start = doc.getLineStartOffset(line);
    int end = doc.getLineEndOffset(line);
    return doc.getText(new TextRange(start, end));
  }
}
