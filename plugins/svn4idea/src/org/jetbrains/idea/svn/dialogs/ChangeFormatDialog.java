// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.WorkingCopyFormat;

import javax.swing.*;
import java.awt.*;
import java.io.File;

import static org.jetbrains.idea.svn.SvnBundle.message;

public class ChangeFormatDialog extends UpgradeFormatDialog {
  private final boolean myWcRootIsAbove;

  public ChangeFormatDialog(final Project project, final File path, final boolean canBeParent, final boolean wcRootIsAbove) {
    super(project, path, canBeParent, false);
    myWcRootIsAbove = wcRootIsAbove;
    setTitle(message("dialog.title.convert.working.copy.format"));
    init();
  }

  @Nullable
  @Override
  protected JPanel getBottomAuxiliaryPanel() {
    if (! myWcRootIsAbove) {
      return null;
    }
    final JPanel result = new JPanel(new GridBagLayout());

    GridBagConstraints gb = new GridBagConstraints();

    gb.insets = JBUI.insets(2);
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

    JLabel warningLabel = new JLabel(message("label.working.copy.root.outside.text"));
    warningLabel.setFont(warningLabel.getFont().deriveFont(Font.BOLD));
    warningLabel.setUI(new MultiLineLabelUI());
    result.add(warningLabel);

    return result;
  }

  @Override
  protected @NotNull String getTopMessage() {
    return message("label.configure.change.label", myPath.getAbsolutePath());
  }

  @Override
  protected @NlsContexts.RadioButton @NotNull String getFormatText(@NotNull WorkingCopyFormat format) {
    return message(switch (format) {
      case ONE_DOT_SIX -> "radio.configure.change.auto.16format";
      case ONE_DOT_SEVEN -> "radio.configure.change.auto.17format";
      case ONE_DOT_EIGHT -> "radio.configure.change.auto.18format";
      default -> throw new IllegalArgumentException("unsupported format " + format);
    });
  }

  @Override
  protected boolean showHints() {
    return false;
  }
}
