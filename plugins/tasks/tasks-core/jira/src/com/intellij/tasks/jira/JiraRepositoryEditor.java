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
package com.intellij.tasks.jira;

import com.intellij.openapi.project.Project;
import com.intellij.tasks.TaskApiBundle;
import com.intellij.tasks.TaskBundle;
import com.intellij.tasks.config.BaseRepositoryEditor;
import com.intellij.tasks.jira.jql.JqlLanguage;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.Consumer;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Mikhail Golubev
 */
public class JiraRepositoryEditor extends BaseRepositoryEditor<JiraRepository> {
  private EditorTextField mySearchQueryField;
  private JBLabel mySearchLabel;
  private JBLabel myNoteLabel;
  private JCheckBox myUseBearerTokenAuthenticationCheckBox;

  public JiraRepositoryEditor(Project project, JiraRepository repository, Consumer<? super JiraRepository> changeListener) {
    super(project, repository, changeListener);
  }

  @Override
  public void apply() {
    myRepository.setSearchQuery(mySearchQueryField.getText());
    myRepository.setUseBearerTokenAuthentication(myUseBearerTokenAuthenticationCheckBox.isSelected());
    super.apply();
    adjustSettingsForServerProperties();
  }

  @Override
  protected void afterTestConnection(boolean connectionSuccessful) {
    super.afterTestConnection(connectionSuccessful);
    if (connectionSuccessful) {
      adjustSettingsForServerProperties();
    }
  }

  @Nullable
  @Override
  protected JComponent createCustomPanel() {
    mySearchQueryField = new LanguageTextField(JqlLanguage.INSTANCE, myProject, myRepository.getSearchQuery());
    installListener(mySearchQueryField);
    mySearchLabel = new JBLabel(TaskBundle.message("label.search"), SwingConstants.RIGHT);
    myNoteLabel = new JBLabel();
    myNoteLabel.setComponentStyle(UIUtil.ComponentStyle.SMALL);
    myUseBearerTokenAuthenticationCheckBox = new JCheckBox(TaskApiBundle.message("use.personal.access.token"));
    myUseBearerTokenAuthenticationCheckBox.setSelected(myRepository.isUseBearerTokenAuthentication());
    myUseBearerTokenAuthenticationCheckBox.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(final ActionEvent e) {
        useBearerTokenChanged();
      }
    });

    adjustSettingsForServerProperties();
    return FormBuilder.createFormBuilder()
      .addComponentToRightColumn(myUseBearerTokenAuthenticationCheckBox)
      .addLabeledComponent(mySearchLabel, mySearchQueryField)
      .addComponentToRightColumn(myNoteLabel)
      .getPanel();
  }

  @Override
  public void setAnchor(@Nullable final JComponent anchor) {
    super.setAnchor(anchor);
    mySearchLabel.setAnchor(anchor);
  }

  private void adjustSettingsForServerProperties() {
    if (myRepository.isJqlSupported()) {
      mySearchQueryField.setEnabled(true);
      myNoteLabel.setVisible(false);
    }
    else {
      mySearchQueryField.setEnabled(false);
      myNoteLabel.setText(
        TaskBundle.message("label.jql.search.cannot.be.used.in.jira.versions.prior.your.version", myRepository.getPresentableVersion()));
      myNoteLabel.setVisible(true);
    }

    if (myRepository.isInCloud()) {
      myUsernameLabel.setVisible(true);
      myUserNameText.setVisible(true);
      myUsernameLabel.setText(TaskBundle.message("label.email"));
      myPasswordLabel.setText(TaskBundle.message("label.api.token"));
      myUseBearerTokenAuthenticationCheckBox.setVisible(false);
    }
    else if (myUseBearerTokenAuthenticationCheckBox.isSelected()) {
      myUsernameLabel.setVisible(false);
      myUserNameText.setVisible(false);
      myPasswordLabel.setText(TaskBundle.message("label.api.token"));
      myUseBearerTokenAuthenticationCheckBox.setVisible(true);
    }
    else {
      myUsernameLabel.setVisible(true);
      myUserNameText.setVisible(true);
      myUsernameLabel.setText(TaskBundle.message("label.username"));
      myPasswordLabel.setText(TaskBundle.message("label.password"));
      myUseBearerTokenAuthenticationCheckBox.setVisible(true);
    }
  }

  protected void useBearerTokenChanged() {
    myRepository.setUseBearerTokenAuthentication(myUseBearerTokenAuthenticationCheckBox.isSelected());
    adjustSettingsForServerProperties();
  }
}
