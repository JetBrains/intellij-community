package com.intellij.openapi.editor.actions;

import com.intellij.find.FindUtil;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorAction;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.project.Project;

public class FindAction extends EditorAction {
  private static class Handler extends EditorActionHandler {
    public void execute(Editor editor, DataContext dataContext) {
      Project project = DataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
      FindUtil.find(project, editor);
    }

    public boolean isEnabled(Editor editor, DataContext dataContext) {
      Project project = DataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(editor.getComponent()));
      return project != null;
    }
  }

  public FindAction() {
    super(new Handler());
  }
}
