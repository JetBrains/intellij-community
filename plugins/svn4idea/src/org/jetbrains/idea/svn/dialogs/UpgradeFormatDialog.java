/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class UpgradeFormatDialog extends DialogWrapper  {
  private JRadioButton myUpgradeAuto16Button;
  private JRadioButton myUpgradeAuto17Button;

  protected File myPath;

  public UpgradeFormatDialog(Project project, File path, boolean canBeParent) {
    this(project, path, canBeParent, true);
  }

  protected UpgradeFormatDialog(Project project, File path, boolean canBeParent, final boolean initHere) {
    super(project, canBeParent);
    myPath = path;
    setResizable(false);
    setTitle(SvnBundle.message("dialog.upgrade.wcopy.format.title"));

    if (initHere) {
      init();
    }
  }

  @NotNull
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "svn.upgradeDialog";
  }

  public void setData(final String selectedFormat) {
    if (SvnConfiguration.UPGRADE_AUTO_17.equals(selectedFormat)) {
      myUpgradeAuto17Button.setSelected(true);
    } else {
      myUpgradeAuto16Button.setSelected(true);
    }
  }

  protected String getTopMessage(final String label) {
    return SvnBundle.message("label.configure." + label + ".label", ApplicationNamesInfo.getInstance().getFullProductName());
  }

  @Nullable
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel();
    panel.setLayout(new GridBagLayout());

    GridBagConstraints gb = new GridBagConstraints();


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

    File adminPath = new File(myPath, SVNFileUtil.getAdminDirectoryName());
    final boolean adminPathIsDirectory = adminPath.isDirectory();
    final String label = getMiddlePartOfResourceKey(adminPathIsDirectory);

    JLabel topLabel = new JLabel(getTopMessage(label));
    topLabel.setUI(new MultiLineLabelUI());
    panel.add(topLabel, gb);
    gb.gridy += 1;


    myUpgradeAuto16Button = new JRadioButton(SvnBundle.message("radio.configure." + label + ".auto.16format"));
    myUpgradeAuto17Button = new JRadioButton(SvnBundle.message("radio.configure." + label + ".auto.17format"));

    ButtonGroup group = new ButtonGroup();
    group.add(myUpgradeAuto16Button);
    group.add(myUpgradeAuto17Button);
    panel.add(myUpgradeAuto16Button, gb);
    gb.gridy += 1;
    panel.add(myUpgradeAuto17Button, gb);
    gb.gridy += 1;

    final JPanel auxiliaryPanel = getBottomAuxiliaryPanel();
    if (auxiliaryPanel != null) {
      panel.add(auxiliaryPanel, gb);
      gb.gridy += 1;
    }

    return panel;
  }

  @Nullable
  protected JPanel getBottomAuxiliaryPanel() {
    return null;
  }

  protected String getMiddlePartOfResourceKey(final boolean adminPathIsDirectory) {
    return ! adminPathIsDirectory ? "create" : "upgrade";
  }

  protected boolean showHints() {
    return true;
  }

  @Nullable
  public String getUpgradeMode() {
    if (myUpgradeAuto17Button.isSelected()) {
      return SvnConfiguration.UPGRADE_AUTO_17;
    } else if (myUpgradeAuto16Button.isSelected()) {
      return SvnConfiguration.UPGRADE_AUTO_16;
    }
    return null;
  }

}
