package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ui.Messages;

public final class PromptMacro extends Macro implements SecondQueueExpandMacro {
  public String getName() {
    return "Prompt";
  }

  public String getDescription() {
    return "Displays a string input dialog";
  }

  public String expand(DataContext dataContext) throws ExecutionCancelledException {
    String userInput = Messages.showInputDialog("Enter parameters:", "Input", Messages.getQuestionIcon());
    if (userInput == null) throw new ExecutionCancelledException();
    return userInput;
  }

  public void cachePreview(DataContext dataContext) {
    myCachedPreview = "<params>";
  }
}
