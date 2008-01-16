package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.fileEditor.impl.EditorComposite;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;

public class CloseAllUnmodifiedEditorsAction extends CloseEditorsActionBase {

  protected boolean isFileToClose(final EditorComposite editor, final EditorWindow window) {
    return !window.getManager().isChanged (editor);
  }


  protected boolean isValidForProject(final Project project) {
    return ProjectLevelVcsManager.getInstance(project).getAllActiveVcss().length > 0;
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
