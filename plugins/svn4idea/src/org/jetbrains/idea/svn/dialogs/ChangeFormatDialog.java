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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.MultiLineLabelUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class ChangeFormatDialog extends UpgradeFormatDialog {
  private final boolean myWcRootIsAbove;

  public ChangeFormatDialog(final Project project, final File path, final boolean canBeParent, final boolean wcRootIsAbove) {
    super(project, path, canBeParent, false);
    myWcRootIsAbove = wcRootIsAbove;
    setTitle(SvnBundle.message("action.change.wcopy.format.task.title"));
    init();
  }

  @NotNull
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
    return SvnBundle.message("label.configure.change.label", myPath.getAbsolutePath());
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
