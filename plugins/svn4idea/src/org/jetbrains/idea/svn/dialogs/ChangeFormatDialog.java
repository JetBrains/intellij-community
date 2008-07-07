package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class ChangeFormatDialog extends UpgradeFormatDialog {
  private boolean myWcRootIsAbove;

  public ChangeFormatDialog(final Project project, final File path, final boolean canBeParent, final boolean wcRootIsAbove) {
    super(project, path, canBeParent, false);
    myWcRootIsAbove = wcRootIsAbove;
    setTitle(SvnBundle.message("action.change.wcopy.format.task.title"));
    init();
  }

  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(),getCancelAction()};
  }

  @Nullable
  @Override
  protected JPanel getBottomAuxiliaryPanel() {
    if (! myWcRootIsAbove) {
      return null;
    }
    final JPanel result = new JPanel(new GridBagLayout());

    GridBagConstraints gb = new GridBagConstraints();

    gb.insets = new Insets(2, 2, 2, 2);
    gb.weightx = 1;
    gb.weighty = 0;
    gb.gridwidth = 2;
    gb.gridheight = 1;
    gb.gridx = 0;
    gb.gridy = 0;
    gb.anchor = GridBagConstraints.WEST;
    gb.fill = GridBagConstraints.HORIZONTAL;

    final JLabel iconLabel = new JLabel(Messages.getWarningIcon());
    result.add(iconLabel, gb);
    ++ gb.gridx;

    JLabel warningLabel = new JLabel(SvnBundle.message("label.working.copy.root.outside.text"));
    warningLabel.setFont(warningLabel.getFont().deriveFont(Font.BOLD));
    warningLabel.setUI(new MultiLineLabelUI());
    result.add(warningLabel);

    return result;
  }

  @Override
  protected String getTopMessage(final String label) {
    return SvnBundle.message("label.configure.change.strategy", myPath.getAbsolutePath());
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
