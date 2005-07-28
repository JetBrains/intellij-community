
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

public class CloseEditorAction extends AnAction {
  public void actionPerformed(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    CommandProcessor.getInstance().executeCommand(
        project, new Runnable(){
        public void run() {
          EditorWindow window = (EditorWindow)dataContext.getData(DataConstantsEx.EDITOR_WINDOW);
          if (window != null) {
            final VirtualFile vFile = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
            closeFileInWindow(vFile, window);
          }
          else {
            final FileEditorManagerEx fileEditorManager = ((FileEditorManagerEx)FileEditorManager.getInstance(project));
            window = fileEditorManager.getCurrentWindow();
            if (window != null) {
              final VirtualFile vFile = window.getSelectedFile();
              closeFileInWindow(vFile, window);
            }
          }
        }
      }, "Close Active Editor", null
    );
  }

  private void closeFileInWindow(final VirtualFile vFile, final EditorWindow window) {
    if (vFile != null && window.isFileOpen(vFile)) {
      window.closeFile(vFile);
    }
  }

  public void update(final AnActionEvent event){
    final Presentation presentation = event.getPresentation();
    final DataContext dataContext = event.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    if (ActionPlaces.EDITOR_POPUP.equals(event.getPlace()) || ActionPlaces.EDITOR_TAB_POPUP.equals(event.getPlace())) {
      presentation.setText("_Close");
    }
    EditorWindow window = (EditorWindow)dataContext.getData(DataConstantsEx.EDITOR_WINDOW);
    if (window == null) {
      window = ((FileEditorManagerEx)FileEditorManager.getInstance(project)).getCurrentWindow();
    }
    presentation.setEnabled(window != null && window.getTabCount() > 0);
  }
}
