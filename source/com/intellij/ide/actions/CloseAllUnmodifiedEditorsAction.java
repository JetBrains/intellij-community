
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorsSplitters;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FileStatusManager;
import com.intellij.openapi.vcs.FileStatus;

import java.util.ArrayList;

public class CloseAllUnmodifiedEditorsAction extends AnAction {

  private ArrayList<Pair<EditorComposite, EditorWindow>> getFilesToClose (final AnActionEvent event) {
    final ArrayList<Pair<EditorComposite, EditorWindow>> res = new ArrayList<Pair<EditorComposite, EditorWindow>>();
    final Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    final FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(project);
    final EditorWindow[] windows = editorManager.getWindows ();
    final FileStatusManager fileStatusManager = FileStatusManager.getInstance(project);
    if (fileStatusManager != null) {
      for (int i = 0; i != windows.length; ++ i) {
        final EditorWindow window = windows [i];
        final EditorComposite [] editors = window.getEditors ();
        for (int j = 0; j < editors.length; j++) {
          final EditorComposite editor = editors [j];
          if (!editorManager.isChanged (editor)) {
            res.add (Pair.create (editor, window));
          }
        }
      }
    }
    return res;
  }


  public void actionPerformed(final AnActionEvent e) {
    final Project project = (Project)e.getDataContext().getData(DataConstants.PROJECT);
    final CommandProcessor commandProcessor = CommandProcessor.getInstance();
    commandProcessor.executeCommand(
      project, new Runnable(){
        public void run() {
          final ArrayList<Pair<EditorComposite, EditorWindow>> filesToClose = getFilesToClose (e);
          for (int i = 0; i != filesToClose.size (); ++ i) {
            final Pair<EditorComposite, EditorWindow> we = filesToClose.get(i);
            we.getSecond ().closeFile (we.getFirst ().getFile ());
          }
        }
      }, "Close All Unmodified Editors", null
    );
  }
  
  public void update(final AnActionEvent event){
    final Presentation presentation = event.getPresentation();
    final Project project = (Project)event.getDataContext().getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(getFilesToClose (event).size () > 0);
  }
}
