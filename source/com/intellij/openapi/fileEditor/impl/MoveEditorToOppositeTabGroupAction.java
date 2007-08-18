package com.intellij.openapi.fileEditor.impl;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * User: anna
 * Date: Apr 18, 2005
 */
public class MoveEditorToOppositeTabGroupAction extends AnAction{

  public void actionPerformed(final AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    final VirtualFile vFile = DataKeys.VIRTUAL_FILE.getData(dataContext);
    final Project project = DataKeys.PROJECT.getData(dataContext);
    if (vFile == null || project == null){
      return;
    }
    final EditorWindow window = (EditorWindow)dataContext.getData(DataConstantsEx.EDITOR_WINDOW);
    if (window != null) {
      final EditorWindow[] siblings = window.findSiblings ();
      if (siblings != null && siblings.length == 1) {

        ((FileEditorManagerImpl)FileEditorManagerEx.getInstanceEx(project)).openFileImpl3 (siblings [0], vFile, true, null);
        window.closeFile(vFile);
      }
    }
  }

  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final DataContext dataContext = e.getDataContext();
    final VirtualFile vFile = DataKeys.VIRTUAL_FILE.getData(dataContext);
    final EditorWindow window = (EditorWindow)dataContext.getData(DataConstantsEx.EDITOR_WINDOW);
    if (vFile != null && window != null) {
      final EditorWindow[] siblings = window.findSiblings ();
      if (siblings != null && siblings.length == 1) {
        presentation.setEnabled(true);
      }
      else {
        presentation.setEnabled(false);
      }
      return;
    }
    presentation.setEnabled(false);
  }
}
