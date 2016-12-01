/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.edu.learning.stepic;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.settings.StudyOptionsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

public class StepicStudyOptions implements StudyOptionsProvider {
  private static final Logger LOG = Logger.getInstance(StepicStudyOptions.class);
  private JTextField myLoginTextField;
  private JPasswordField myPasswordField;
  private JPanel myPane;
  private JBCheckBox myEnableTestingFromSamples;

  public StepicStudyOptions() {
    myLoginTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        erasePassword();
      }
    });
  }

  private void erasePassword() {
    setPassword("");
  }

  @NotNull
  public JComponent getPanel() {
    return myPane;
  }

  @NotNull
  public String getLogin() {
    return myLoginTextField.getText().trim();
  }

  public void setLogin(@Nullable final String login) {
    myLoginTextField.setText(login);
  }

  @NotNull
  private String getPassword() {
    return String.valueOf(myPasswordField.getPassword());
  }

  private void setPassword(@NotNull final String password) {
    myPasswordField.setText(password);
  }
  
  private boolean isTestingFromSamplesEnabled() {
    return myEnableTestingFromSamples.isSelected();
  }

  @Override
  public void reset() {
    Project project = StudyUtils.getStudyProject();
    if (project != null) {
      StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
      final StepicUser user = taskManager.getUser();
      setLogin(user.getEmail());
      setPassword(user.getPassword());
      myEnableTestingFromSamples.setSelected(taskManager.isEnableTestingFromSamples());
    }
    else {
      LOG.warn("No study object is opened");
    }
  }

  @Override
  public void disposeUIResources() {

  }

  @Override
  public void apply() throws ConfigurationException {
    if (isModified()) {
      final Project project = StudyUtils.getStudyProject();
      if (project != null) {
        StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
        taskManager.setEnableTestingFromSamples(myEnableTestingFromSamples.isSelected());
        
        final String login = getLogin();
        final String password = getPassword();
        if (!StringUtil.isEmptyOrSpaces(login) && !StringUtil.isEmptyOrSpaces(password)) {
          // login to post credentials
          final StepicUser[] stepicUser = new StepicUser[1];
          ProgressManager.getInstance().runProcessWithProgressSynchronously(
            () -> {
              ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
              stepicUser[0] = StudyUtils.execCancelable(() -> EduStepicAuthorizedClient.login(login, password));
            }, "Logging In", true,
            project);

          if (stepicUser[0] != null && stepicUser[0].getAccessToken() != null) {
            taskManager.setUser(stepicUser[0]);
          }
          else {
            throw new ConfigurationException("Unable to login");
          }
        }
      }
      else {
        LOG.warn("No study object is opened");
      }
    }
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myPane;
  }

  public boolean isModified() {
    final Project project = StudyUtils.getStudyProject();
    if (project == null) return false;

    final StudyTaskManager taskManager = StudyTaskManager.getInstance(project);
    final StepicUser user = taskManager.getUser();
    
    return !getLogin().equals(user.getEmail()) 
           || !getPassword().equals(user.getPassword())
           || !isTestingFromSamplesEnabled() == taskManager.isEnableTestingFromSamples(); 
  }

  private void createUIComponents() {
    Document doc = new PlainDocument();
    myPasswordField = new JPasswordField(doc, null, 0);
  }
}
