package com.intellij.unscramble;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;

public final class UnscrambleFromClipboardAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = DataKeys.PROJECT.getData(e.getDataContext());

    // If there's a text in clipboard and log is specified or not needed, unscramble w/o extra questions

    String text = UnscrambleDialog.getTextInClipboard();
    if (text != null) {
      String file = UnscrambleDialog.getLastUsedLogUrl();
      if (file != null && file.trim().length() == 0) {
        file = null;
      }
      UnscrambleSupport savedUnscrambler = UnscrambleDialog.getSavedUnscrambler();
      if (savedUnscrambler == null || file != null) {
        boolean success = UnscrambleDialog.showUnscrambledText(savedUnscrambler, file, project, text);
        if (success) {
          return;
        }
      }
    }

    // Use regular unscramble dialog
    UnscrambleDialog dialog = new UnscrambleDialog(project);
    dialog.show();
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = DataKeys.PROJECT.getData(event.getDataContext());
    presentation.setEnabled(project != null);
  }
}
