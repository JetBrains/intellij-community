
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class CloseAllEditorsButActiveAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    FileEditorManagerEx fileEditorManager=FileEditorManagerEx.getInstanceEx(project);
    VirtualFile selectedFile=fileEditorManager.getSelectedFiles()[0];
    VirtualFile[] siblings = fileEditorManager.getSiblings(selectedFile);
    for(int i=0;i<siblings.length;i++){
      if(selectedFile!=siblings[i]){
        fileEditorManager.closeFile(siblings[i]);
      }
    }
  }

  public void update(AnActionEvent event){
    Presentation presentation = event.getPresentation();
    Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    if (ActionPlaces.EDITOR_POPUP.equals(event.getPlace())) {
      presentation.setText("Close All But Current");
    }
    FileEditorManagerEx fileEditorManager = FileEditorManagerEx.getInstanceEx(project);
    if (fileEditorManager.getSelectedFiles().length == 0) {
      presentation.setEnabled(false);
      return;
    }
    VirtualFile[] siblings = fileEditorManager.getSiblings(fileEditorManager.getSelectedFiles()[0]);
    presentation.setEnabled(siblings.length > 1);
  }
}
