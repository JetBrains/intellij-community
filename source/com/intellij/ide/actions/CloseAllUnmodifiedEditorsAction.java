
package com.intellij.ide.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.DataConstantsEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vcs.FileStatusManager;

import java.util.ArrayList;

public class CloseAllUnmodifiedEditorsAction extends AnAction {

  private ArrayList<Pair<EditorComposite, EditorWindow>> getFilesToClose (final AnActionEvent event) {
    final ArrayList<Pair<EditorComposite, EditorWindow>> res = new ArrayList<Pair<EditorComposite, EditorWindow>>();
    final DataContext dataContext = event.getDataContext();
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    final FileEditorManagerEx editorManager = FileEditorManagerEx.getInstanceEx(project);
    final EditorWindow editorWindow = (EditorWindow)dataContext.getData(DataConstantsEx.EDITOR_WINDOW);
    final EditorWindow[] windows;
    if (editorWindow != null){
      windows = new EditorWindow[]{ editorWindow };
    } 
    else {
      windows = editorManager.getWindows ();
    }
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
    final DataContext dataContext = event.getDataContext();
    final EditorWindow editorWindow = (EditorWindow)dataContext.getData(DataConstantsEx.EDITOR_WINDOW);
    if (editorWindow != null && editorWindow.inSplitter()) {
      presentation.setText("Close All _Unmodified Editors In Tab Group");
    }
    else {
      presentation.setText("Close All _Unmodified Editors");
    }
    final Project project = (Project)dataContext.getData(DataConstants.PROJECT);
    if (project == null) {
      presentation.setEnabled(false);
      return;
    }
    presentation.setEnabled(getFilesToClose (event).size () > 0);
  }
}
