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

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.jetbrains.edu.learning.StudySettings;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.settings.StudyOptionsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

public class StepicStudyOptions implements StudyOptionsProvider {
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
    myEnableTestingFromSamples.setEnabled(false);
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
    final StudySettings stepikSettings = StudySettings.getInstance();
    myEnableTestingFromSamples.setSelected(stepikSettings.isEnableTestingFromSamples());
    final StepicUser user = stepikSettings.getUser();
    if (user != null) {
      setLogin(user.getEmail());
      setPassword(user.getPassword());
    }
  }

  @Override
  public void disposeUIResources() {

  }

  @Override
  public void apply() throws ConfigurationException {
    final StudySettings stepikSettings = StudySettings.getInstance();
    if (isTestingFromSamplesEnabled() != stepikSettings.isEnableTestingFromSamples()) {
      stepikSettings.setEnableTestingFromSamples(isTestingFromSamplesEnabled());
    }

    final StepicUser user = stepikSettings.getUser();
    String savedEmail = user == null ? "" : user.getEmail();
    String savedPassword = user == null ? "" : user.getPassword();
    final boolean isCredentialsModified = !getLogin().equals(savedEmail) || !getPassword().equals(savedPassword);
    if (isCredentialsModified) {
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
          null);

        if (stepicUser[0] != null) {
          stepikSettings.setUser(stepicUser[0]);
        }
        else {
          throw new ConfigurationException("Unable to login");
        }
      }
      else {
        removeCredentials();
      }
    }
  }

  private static void removeCredentials() {
    StudySettings.getInstance().setUser(null);
    EduStepicAuthorizedClient.invalidateClient();
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myPane;
  }

  public boolean isModified() {
    final StudySettings stepikSettings = StudySettings.getInstance();
    boolean isTestOptionModified = !isTestingFromSamplesEnabled() == stepikSettings.isEnableTestingFromSamples();
    final StepicUser user = stepikSettings.getUser();

    String email = user == null ? "" : user.getEmail();
    String password = user == null ? "" : user.getPassword();

    return !getLogin().equals(email)
           || !getPassword().equals(password)
           || isTestOptionModified;
  }

  private void createUIComponents() {
    Document doc = new PlainDocument();
    myPasswordField = new JPasswordField(doc, null, 0);
  }
}
