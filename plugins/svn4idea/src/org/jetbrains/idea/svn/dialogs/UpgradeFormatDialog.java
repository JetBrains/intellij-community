// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.WorkingCopyFormat;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.jetbrains.idea.svn.SvnBundle.message;
import static org.jetbrains.idea.svn.SvnUtil.seemsLikeVersionedDir;

public class UpgradeFormatDialog extends DialogWrapper  {

  private final ButtonGroup formatGroup = new ButtonGroup();
  private final List<JRadioButton> formatButtons = new ArrayList<>();

  private JBLoadingPanel myLoadingPanel;

  protected final File myPath;
  private final boolean isVersioned;

  public UpgradeFormatDialog(Project project, File path, boolean canBeParent) {
    this(project, path, canBeParent, true);
  }

  protected UpgradeFormatDialog(Project project, File path, boolean canBeParent, final boolean initHere) {
    super(project, canBeParent);
    myPath = path;
    isVersioned = seemsLikeVersionedDir(myPath);

    setResizable(false);
    setTitle(message("dialog.upgrade.wcopy.format.title"));

    if (initHere) {
      init();
    }
  }

  @Override
  protected Action @NotNull [] createActions() {
    return new Action[]{getOKAction(), getCancelAction()};
  }

  @Override
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

  protected @NlsContexts.Label @NotNull String getTopMessage() {
    return isVersioned
           ? message("label.configure.upgrade.label")
           : message("label.configure.create.label", ApplicationNamesInfo.getInstance().getFullProductName());
  }

  protected @NlsContexts.RadioButton @NotNull String getFormatText(@NotNull WorkingCopyFormat format) {
    return message(switch (format) {
      case ONE_DOT_SIX -> isVersioned ? "radio.configure.upgrade.auto.16format" : "radio.configure.create.auto.16format";
      case ONE_DOT_SEVEN -> isVersioned ? "radio.configure.upgrade.auto.17format" : "radio.configure.create.auto.17format";
      case ONE_DOT_EIGHT -> isVersioned ? "radio.configure.upgrade.auto.18format" : "radio.configure.create.auto.18format";
      default -> throw new IllegalArgumentException("unsupported format " + format);
    });
  }

  @Override
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

    JLabel topLabel = new JLabel(getTopMessage());
    topLabel.setUI(new MultiLineLabelUI());
    panel.add(topLabel, gb);
    gb.gridy += 1;

    registerFormat(WorkingCopyFormat.ONE_DOT_SIX, panel, gb);
    registerFormat(WorkingCopyFormat.ONE_DOT_SEVEN, panel, gb);
    registerFormat(WorkingCopyFormat.ONE_DOT_EIGHT, panel, gb);

    final JPanel auxiliaryPanel = getBottomAuxiliaryPanel();
    if (auxiliaryPanel != null) {
      panel.add(auxiliaryPanel, gb);
      gb.gridy += 1;
    }

    myLoadingPanel = new JBLoadingPanel(new BorderLayout(), getDisposable());
    myLoadingPanel.add(panel, BorderLayout.CENTER);

    return myLoadingPanel;
  }

  private void registerFormat(@NotNull WorkingCopyFormat format, @NotNull JPanel panel, @NotNull GridBagConstraints gb) {
    JRadioButton button = new JRadioButton(getFormatText(format));
    button.putClientProperty("format", format);

    panel.add(button, gb);
    gb.gridy += 1;

    formatGroup.add(button);
    formatButtons.add(button);
  }

  @Nullable
  protected JPanel getBottomAuxiliaryPanel() {
    return null;
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
