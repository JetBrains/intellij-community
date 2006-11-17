package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class UpgradeFormatDialog extends DialogWrapper  {
  private JRadioButton myUpgradeNoneButton;
  private JRadioButton myUpgradeAutoButton;

  private File myPath;

  public UpgradeFormatDialog(Project project, File path, boolean canBeParent) {
    super(project, canBeParent);
    myPath = path;
    setResizable(false);
    setTitle("Subversion Working Copy Upgrade");
    init();
  }

  protected Action[] createActions() {
    return new Action[]{getOKAction()};
  }


  @NonNls
  protected String getDimensionServiceKey() {
    return "svn.upgradeDialog";
  }

  @Nullable
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    GridBagConstraints gb = new GridBagConstraints();

    File adminPath = new File(myPath, SVNFileUtil.getAdminDirectoryName());
    String label = !adminPath.isDirectory() ? "create" : "upgrade";

    // top label.
    gb.insets = new Insets(2, 2, 2, 2);
    gb.weightx = 1;
    gb.weighty = 0;
    gb.gridwidth = 2;
    gb.gridheight = 1;
    gb.gridx = 0;
    gb.gridy = 0;
    gb.anchor = GridBagConstraints.WEST;
    gb.fill = GridBagConstraints.HORIZONTAL;

    JLabel topLabel = new JLabel(SvnBundle.message(new StringBuilder().append("label.configure.").append(label).append(".label").toString()));
    topLabel.setUI(new MultiLineLabelUI());
    panel.add(topLabel, gb);
    gb.gridy += 1;
    JLabel warningLabel = new JLabel(!adminPath.isDirectory() ? SvnBundle.message("label.configure.create.warning") : SvnBundle.message("label.configure.upgrade.warning"));
    warningLabel.setFont(warningLabel.getFont().deriveFont(Font.BOLD));
    warningLabel.setUI(new MultiLineLabelUI());
    panel.add(warningLabel, gb);
    gb.gridy += 1;

    myUpgradeNoneButton = new JRadioButton(SvnBundle.message(new StringBuilder().append("radio.configure.").append(label).append(".none").toString()));
    myUpgradeAutoButton = new JRadioButton(SvnBundle.message(new StringBuilder().append("radio.configure.").append(label).append(".auto").toString()));

    ButtonGroup group = new ButtonGroup();
    group.add(myUpgradeNoneButton);
    group.add(myUpgradeAutoButton);
    panel.add(myUpgradeNoneButton, gb);
    gb.gridy += 1;
    panel.add(myUpgradeAutoButton, gb);
    gb.gridy += 1;
    JLabel settingsLabel = new JLabel("To change above setting later, visit 'File | Settings | Version Control'");
    settingsLabel.setEnabled(false);
    panel.add(settingsLabel, gb);
    gb.gridy += 1;
    panel.add(new JSeparator(), gb);

    myUpgradeNoneButton.setSelected(true);
    return panel;
  }

  public String getUpgradeMode() {
    if (myUpgradeNoneButton.isSelected()) {
      return SvnConfiguration.UPGRADE_NONE;
    } else if (myUpgradeAutoButton.isSelected()) {
      return SvnConfiguration.UPGRADE_AUTO;
    }
    return null;
  }
}
