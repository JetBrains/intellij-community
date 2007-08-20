package com.intellij.openapi.editor.actions;

import com.intellij.find.EditorSearchComponent;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;

import javax.swing.*;

public class IncrementalFindAction extends EditorAction {
  private static class Handler extends EditorActionHandler {
    public void execute(final Editor editor, DataContext dataContext) {
      final Project project = DataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
      if (!editor.isOneLineMode()) {
        final EditorSearchComponent header = new EditorSearchComponent(editor, project);
        editor.setHeaderComponent(header);
        
        SwingUtilities.invokeLater(new Runnable() {
          public void run() {
            header.requestFocus();
          }
        });
      }
    }

    public boolean isEnabled(Editor editor, DataContext dataContext) {
      Project project = DataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
      return project != null && !editor.isOneLineMode();
    }
  }

  public IncrementalFindAction() {
    super(new Handler());
  }
}