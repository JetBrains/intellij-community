/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.tasks.trac;

import com.intellij.openapi.project.Project;
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

  public TracRepositoryEditor(final Project project, final TracRepository repository, Consumer<TracRepository> changeListener) {
    super(project, repository, changeListener);
    myDefaultSearch.setText(repository.getDefaultSearch());
  }

  @Override
  public void apply() {
    myRepository.setDefaultSearch(myDefaultSearch.getText());
    super.apply();
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    mySearchLabel = new JBLabel("Search:", SwingConstants.RIGHT);
    myDefaultSearch = new JTextField();
    installListener(myDefaultSearch);
    return FormBuilder.createFormBuilder().addLabeledComponent(mySearchLabel, myDefaultSearch).getPanel();
  }

  @Override
  public void setAnchor(@Nullable final JComponent anchor) {
    super.setAnchor(anchor);
    mySearchLabel.setAnchor(anchor);
  }
}
