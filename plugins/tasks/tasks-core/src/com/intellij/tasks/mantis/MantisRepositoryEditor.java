/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.tasks.mantis;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.UriUtil;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * User: evgeny.zakrevsky
 * Date: 9/21/12
 */
public class MantisRepositoryEditor extends BaseRepositoryEditor<MantisRepository> {
  private ComboBox myProjectCombobox;
  private ComboBox myFilterCombobox;
  private JBLabel myProjectLabel;
  private JBLabel myFilterLabel;


  public MantisRepositoryEditor(final Project project, final MantisRepository repository, final Consumer<MantisRepository> changeListener) {
    super(project, repository, changeListener);
    myTestButton.setText("Login");
  }


  @Override
  public void apply() {
    if (!myRepository.getUrl().equals(UriUtil.trimTrailingSlashes(myURLText.getText())) ||
        !myRepository.getUsername().equals(myUserNameText.getText()) ||
        !myRepository.getPassword().equals(myPasswordText.getText())) {
      resetComboBoxes();
    }
    else {
      final Object selectedProjectObject = myProjectCombobox.getModel().getSelectedItem();
      final Object selectedFilterObject = myFilterCombobox.getModel().getSelectedItem();
      if (selectedProjectObject != null &&
          selectedFilterObject != null &&
          selectedProjectObject instanceof MantisProject &&
          selectedFilterObject instanceof MantisFilter) {
        myRepository.setProject((MantisProject)selectedProjectObject);
        myRepository.setFilter((MantisFilter)selectedFilterObject);
      }
    }
    super.apply();
  }

  private void resetComboBoxes() {
    myProjectCombobox.setModel(new DefaultComboBoxModel(new Object[]{"Login before"}));
    myFilterCombobox.setModel(new DefaultComboBoxModel(new Object[]{"Login before"}));
    myRepository.setProject(null);
    myRepository.setFilter(null);
  }

  @Override
  protected void afterTestConnection(final boolean connectionSuccessful) {
    super.afterTestConnection(connectionSuccessful);
    if (connectionSuccessful) {
      updateProjects();
    }
  }

  @Override
  public void setAnchor(@Nullable final JComponent anchor) {
    super.setAnchor(anchor);
    myProjectLabel.setAnchor(anchor);
    myFilterLabel.setAnchor(anchor);
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    myProjectLabel = new JBLabel("Project:", SwingConstants.RIGHT);
    myProjectCombobox = new ComboBox(200);
    myProjectCombobox.addItemListener(new ItemListener() {
      @Override
      public void itemStateChanged(final ItemEvent e) {
        updateFilters();
      }
    });
    installListener(myProjectCombobox);
    myFilterLabel = new JBLabel("Filter:", SwingConstants.RIGHT);
    myFilterCombobox = new ComboBox(200);
    installListener(myFilterCombobox);
    updateProjects();
    return FormBuilder.createFormBuilder().addLabeledComponent(myProjectLabel, myProjectCombobox)
      .addLabeledComponent(myFilterLabel, myFilterCombobox).getPanel();
  }

  private void updateProjects() {
    try {
      myProjectCombobox.setModel(new DefaultComboBoxModel(myRepository.getProjects().toArray()));
      if (myRepository.getProject() != null) myProjectCombobox.setSelectedItem(myRepository.getProject());
      updateFilters();
    }
    catch (Exception e) {
      resetComboBoxes();
    }
  }

  private void updateFilters() {
    try {
      final Object selectedItem = myProjectCombobox.getModel().getSelectedItem();
      if (selectedItem != null && selectedItem instanceof MantisProject) {
        myFilterCombobox.setModel(new DefaultComboBoxModel(myRepository.getFilters((MantisProject)selectedItem).toArray()));
        if (myRepository.getFilter() != null) myFilterCombobox.setSelectedItem(myRepository.getFilter());
        doApply();
      }
    }
    catch (Exception e) {
      resetComboBoxes();
    }
  }
}
