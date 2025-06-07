// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.lighthouse;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dennis.Ushakov
 */
public class LighthouseRepositoryEditor extends BaseRepositoryEditor<LighthouseRepository> {
  private JTextField myProjectId;
  private JBLabel myProjectIDLabel;

  public LighthouseRepositoryEditor(final Project project,
                                    final LighthouseRepository repository,
                                    Consumer<? super LighthouseRepository> changeListener) {
    super(project, repository, changeListener);
    myUserNameText.setVisible(false);
    myUsernameLabel.setVisible(false);
    myPasswordLabel.setText(TaskBundle.message("label.api.token"));

    myProjectId.setText(repository.getProjectId());
  }

  @Override
  public void apply() {
    myRepository.setProjectId(myProjectId.getText().trim());
    super.apply();
  }

  @Override
  protected @Nullable JComponent createCustomPanel() {
    myProjectIDLabel = new JBLabel(TaskBundle.message("label.project.id"), SwingConstants.RIGHT);
    myProjectId = new JTextField();
    installListener(myProjectId);
    return FormBuilder.createFormBuilder()
      .addLabeledComponent(myProjectIDLabel, myProjectId)
      .getPanel();
  }

  @Override
  public void setAnchor(final @Nullable JComponent anchor) {
    super.setAnchor(anchor);
    myProjectIDLabel.setAnchor(anchor);
  }
}
