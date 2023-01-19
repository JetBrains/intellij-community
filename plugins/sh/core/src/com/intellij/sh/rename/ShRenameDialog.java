// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.rename;

import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.refactoring.ui.RefactoringDialog;
import com.intellij.util.ui.JBUI;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

class ShRenameDialog extends RefactoringDialog {

  private final JLabel myNameLabel;
  private final NameSuggestionsField myNameSuggestionsField;
  private final TextOccurrencesRenamer myRenamer;

  ShRenameDialog(@NotNull Project project, @NotNull TextOccurrencesRenamer renamer) {
    super(project, false);
    myRenamer = renamer;
    String nameLabelText = RefactoringBundle.message("rename.0.and.its.usages.to", "'" + renamer.getOldName() + "'");
    myNameLabel = new JLabel(XmlStringUtil.escapeString(nameLabelText, false));
    myNameSuggestionsField = new NameSuggestionsField(new String[] {renamer.getOldName()},
                                                      myProject, FileTypes.PLAIN_TEXT, renamer.getEditor()) {
      @Override
      protected boolean shouldSelectAll() {
        return renamer.getEditor().getSettings().isPreselectRename();
      }
    };
    setTitle(RefactoringBundle.message("rename.title"));
    init();
  }

  @Override
  protected void doAction() {
    close(DialogWrapper.OK_EXIT_CODE);
    myRenamer.renameTo(getNewName());
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myNameSuggestionsField.getFocusableComponent();
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    JPanel panel = new JPanel(new GridBagLayout());
    panel.add(myNameLabel, new GridBagConstraints(
      0, 0,
      1, 1,
      1.0, 0.0,
      GridBagConstraints.CENTER,
      GridBagConstraints.BOTH,
      JBUI.insetsBottom(4),
      0, 0
    ));
    panel.add(myNameSuggestionsField.getComponent(), new GridBagConstraints(
      0, 1,
      1, 1,
      1.0, 0.0,
      GridBagConstraints.CENTER,
      GridBagConstraints.BOTH,
      JBUI.insetsBottom(8),
      0, 0
    ));
    return panel;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return null;
  }

  @Override
  protected boolean hasPreviewButton() {
    return false;
  }

  @Override
  protected boolean hasHelpAction() {
    return false;
  }

  @NotNull
  private String getNewName() {
    return myNameSuggestionsField.getEnteredName().trim();
  }

  @Override
  protected void canRun() throws ConfigurationException {
    super.canRun();
    if (myRenamer.getOldName().equals(getNewName())) throw new ConfigurationException(null);
  }
}
