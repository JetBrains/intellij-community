
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class CloseAllEditorsAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(
      project, new Runnable(){
        public void run() {
          FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(project);
          VirtualFile selectedFile = editorManager.getSelectedFiles()[0];
          VirtualFile[] openFiles = editorManager.getSiblings(selectedFile);
          for (int i = 0; i < openFiles.length; i++) {
            editorManager.closeFile(openFiles[i]);
          }
        }
      }, "Close All Editors", null
    );
  }
  
  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(FileEditorManager.getInstance(project).getSelectedFiles().length > 0);
  }
}
