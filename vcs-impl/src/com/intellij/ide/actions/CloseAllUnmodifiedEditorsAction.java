package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.actionSystem.AnActionEvent;

public class CloseAllUnmodifiedEditorsAction extends CloseEditorsActionBase {

  protected boolean isFileToClose(final EditorComposite editor, final EditorWindow window) {
    return !window.getManager().isChanged (editor);
  }

  @Override
  protected boolean isActionEnabled(final Project project, final AnActionEvent event) {
    return super.isActionEnabled(project, event) && ProjectLevelVcsManager.getInstance(project).getAllActiveVcss().length > 0;
  }

  protected String getPresentationText(final boolean inSplitter) {
    if (inSplitter) {
      return IdeBundle.message("action.close.all.unmodified.editors.in.tab.group");
    }
    else {
      return IdeBundle.message("action.close.all.unmodified.editors");
    }
  }
}
