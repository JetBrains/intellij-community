// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tasks.trac;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Avdeev
 */
public class TracRepositoryEditor extends BaseRepositoryEditor<TracRepository> {
  private JTextField myDefaultSearch;
  private JBLabel mySearchLabel;

  public TracRepositoryEditor(final Project project, final TracRepository repository, Consumer<? super TracRepository> changeListener) {
    super(project, repository, changeListener);
    myDefaultSearch.setText(repository.getDefaultSearch());
  }

  @Override
  public void apply() {
    myRepository.setDefaultSearch(myDefaultSearch.getText());
    super.apply();
  }

  @Override
  protected @Nullable JComponent createCustomPanel() {
    mySearchLabel = new JBLabel(TaskBundle.message("label.search"), SwingConstants.RIGHT);
    myDefaultSearch = new JTextField();
    installListener(myDefaultSearch);
    return FormBuilder.createFormBuilder().addLabeledComponent(mySearchLabel, myDefaultSearch).getPanel();
  }

  @Override
  public void setAnchor(final @Nullable JComponent anchor) {
    super.setAnchor(anchor);
    mySearchLabel.setAnchor(anchor);
  }
}
