package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.*;
import java.io.File;

public class ChangeFormatDialog extends UpgradeFormatDialog {
  public ChangeFormatDialog(final Project project, final File path, final boolean canBeParent) {
    super(project, path, canBeParent);
    setTitle(SvnBundle.message("action.change.wcopy.format.task.title"));
  }

  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(),getCancelAction()};
  }

  @Override
  protected String getMiddlePartOfResourceKey(final boolean adminPathIsDirectory) {
    return "change";
  }

  @Override
  protected boolean showHints() {
    return false;
  }
}
