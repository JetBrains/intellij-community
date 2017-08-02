/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.util.DocumentUtil;
import com.jetbrains.python.console.pydev.ConsoleCommunicationListener;
import org.jetbrains.annotations.NotNull;

public class PyConsoleStartFolding implements ConsoleCommunicationListener, FoldingListener, DocumentListener {
  private PythonConsoleView myConsoleView;
  private int myNumberOfCommandExecuted = 0;
  private int myNumberOfCommandToStop = 2;
  private boolean doNotAddFoldingAgain = false;
  private FoldRegion myStartFoldRegion;
  private static final String DEFAULT_FOLDING_MESSAGE = "Python Console";
  private int myStartLineOffset = 0;

  public PyConsoleStartFolding(PythonConsoleView consoleView) {
    super();
    myConsoleView = consoleView;
  }

  public void setStartLineOffset(int startLineOffset) {
    myStartLineOffset = startLineOffset;
  }

  public void setNumberOfCommandToStop(int numberOfCommandToStop) {
    myNumberOfCommandToStop = numberOfCommandToStop;
  }

  @Override
  public void documentChanged(DocumentEvent event) {
    addFolding(event);
  }

  private void addFolding(DocumentEvent event) {
    Document document = myConsoleView.getEditor().getDocument();
    if (doNotAddFoldingAgain || document.getTextLength() == 0) {
      return;
    }
    if (myNumberOfCommandExecuted >= myNumberOfCommandToStop) {
      document.removeDocumentListener(this);
      return;
    }
    FoldingModel foldingModel = myConsoleView.getEditor().getFoldingModel();
    foldingModel.runBatchFoldingOperation(() -> {
      int start = myStartLineOffset;
      int finish = document.getTextLength() - 1;
      String placeholderText = DEFAULT_FOLDING_MESSAGE;
      int firstLine = document.getLineNumber(myStartLineOffset);
      for (int line = firstLine; line < document.getLineCount(); line++) {
        String lineText = document.getText(DocumentUtil.getLineTextRange(document, line));
        if (lineText.startsWith("Python")) {
          if (start == myStartLineOffset) {
            start = document.getLineStartOffset(line);
          }
          placeholderText = lineText;
          break;
        }
        if (lineText.startsWith("PyDev console")) {
          start = document.getLineStartOffset(line);
        }
      }
      String newFragment = event.getNewFragment().toString();
      if (newFragment.startsWith("In[") || newFragment.startsWith(PyConsoleUtil.ORDINARY_PROMPT)) {
        finish = event.getOffset() - 1;
        doNotAddFoldingAgain = true;
      }
      if (myStartFoldRegion != null) {
        foldingModel.removeFoldRegion(myStartFoldRegion);
      }
      FoldRegion foldRegion = foldingModel.addFoldRegion(start, finish, placeholderText);
      if (foldRegion != null) {
        foldRegion.setExpanded(false);
        myStartFoldRegion = foldRegion;
      }
    });
  }

  @Override
  public void commandExecuted(boolean more) {
    myNumberOfCommandExecuted++;
  }

  @Override
  public void inputRequested() {

  }

  @Override
  public void onFoldRegionStateChange(@NotNull FoldRegion region) {
    if (region.equals(myStartFoldRegion) && region.isExpanded()) {
      myConsoleView.getEditor().getComponent().updateUI();
      doNotAddFoldingAgain = true;
    }
  }

  @Override
  public void onFoldProcessingEnd() {

  }
}
