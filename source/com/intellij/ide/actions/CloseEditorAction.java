
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;

public class CloseEditorAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    CommandProcessor.getInstance().executeCommand(
        project, new Runnable(){
        public void run() {
          final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
          final EditorWindow currentWindow = fileEditorManager.getCurrentWindow ();
          if (currentWindow != null) {
            currentWindow.closeFile(currentWindow.getSelectedFile());
          }
        }
      }, "Close Active Editor", null
    );
  }

  public void update(final AnActionEvent event){
    final Presentation presentation = event.getPresentation();
    final Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    if (ActionPlaces.EDITOR_POPUP.equals(event.getPlace())) {
      presentation.setText("Close");
    }
    final FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    presentation.setEnabled(fileEditorManager.getCurrentWindow() != null);
  }
}
