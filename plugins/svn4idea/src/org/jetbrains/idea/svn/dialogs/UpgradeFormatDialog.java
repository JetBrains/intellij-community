/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.WorkingCopyFormat;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class UpgradeFormatDialog extends DialogWrapper  {

  private ButtonGroup formatGroup = new ButtonGroup();
  private List<JRadioButton> formatButtons = new ArrayList<>();

  private JBLoadingPanel myLoadingPanel;

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

  public void setData(@NotNull final WorkingCopyFormat selectedFormat) {
    for (JRadioButton button : formatButtons) {
      if (selectedFormat == getFormat(button)) {
        button.setSelected(true);
        break;
      }
    }
  }

  public void setSupported(@NotNull Collection<WorkingCopyFormat> supported) {
    for (JRadioButton button : formatButtons) {
      button.setEnabled(supported.contains(getFormat(button)));
    }
  }

  public void startLoading() {
    enableFormatButtons(false);
    getOKAction().setEnabled(false);
    myLoadingPanel.startLoading();
  }

  private void enableFormatButtons(boolean enabled) {
    for (JRadioButton button : formatButtons) {
      button.setEnabled(enabled);
    }
  }

  public void stopLoading() {
    getOKAction().setEnabled(true);
    myLoadingPanel.stopLoading();
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
    gb.insets = JBUI.insets(2);
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

    registerFormat(WorkingCopyFormat.ONE_DOT_SIX, label, panel, gb);
    registerFormat(WorkingCopyFormat.ONE_DOT_SEVEN, label, panel, gb);
    registerFormat(WorkingCopyFormat.ONE_DOT_EIGHT, label, panel, gb);

    final JPanel auxiliaryPanel = getBottomAuxiliaryPanel();
    if (auxiliaryPanel != null) {
      panel.add(auxiliaryPanel, gb);
      gb.gridy += 1;
    }

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), getDisposable());
    myLoadingPanel.add(panel, BorderLayout.CENTER);

    return myLoadingPanel;
  }

  private void registerFormat(@NotNull WorkingCopyFormat format,
                              @NotNull String label,
                              @NotNull JPanel panel,
                              @NotNull GridBagConstraints gb) {
    JRadioButton button = new JRadioButton(SvnBundle.message("radio.configure." + label + ".auto." + getKey(format) + "format"));
    button.putClientProperty("format", format);

    panel.add(button, gb);
    gb.gridy += 1;

    formatGroup.add(button);
    formatButtons.add(button);
  }

  private static String getKey(@NotNull WorkingCopyFormat format) {
    return String.format("%d%d", format.getVersion().major, format.getVersion().minor);
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

  @NotNull
  private static WorkingCopyFormat getFormat(@NotNull JRadioButton button) {
    Object format = button.getClientProperty("format");

    return format instanceof WorkingCopyFormat ? (WorkingCopyFormat)format : WorkingCopyFormat.UNKNOWN;
  }

  @NotNull
  public WorkingCopyFormat getUpgradeMode() {
    WorkingCopyFormat result = WorkingCopyFormat.UNKNOWN;

    for (JRadioButton button : formatButtons) {
      if (button.isSelected()) {
        result = getFormat(button);
        break;
      }
    }

    return result;
  }
}
