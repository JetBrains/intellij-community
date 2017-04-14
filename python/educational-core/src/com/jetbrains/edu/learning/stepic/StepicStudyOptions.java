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

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.edu.learning.StudySettings;
import com.jetbrains.edu.learning.settings.StudyOptionsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class StepicStudyOptions implements StudyOptionsProvider {
  private JPanel myPane;
  private JBCheckBox myEnableTestingFromSamples;
  private JButton myLoginButton;
  private JButton myLogoutButton;
  private JBLabel myUsernameLabel;
  private StepicUser myStepicUser;

  public StepicStudyOptions() {
    StepicUser user = StudySettings.getInstance().getUser();
    myLogoutButton.setEnabled(user != null);
    myLoginButton.addActionListener(new ActionListener() {

      @Override
      public void actionPerformed(ActionEvent e) {
        BrowserUtil.browse(EduStepicNames.IMPLICIT_GRANT_URL);
        OAuthDialog dialog = new OAuthDialog("Authorizing on Stepik");
        if (dialog.showAndGet()) {
          myStepicUser = dialog.getStepicUser();
          updateUsernameLabel(myStepicUser);
        }
      }
    });

    myLogoutButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        removeCredentials();
        updateUsernameLabel(null);
      }
    });
  }

  @NotNull
  public JComponent getPanel() {
    return myPane;
  }

  private boolean isTestingFromSamplesEnabled() {
    return myEnableTestingFromSamples.isSelected();
  }

  @Override
  public void reset() {
    final StudySettings stepikSettings = StudySettings.getInstance();
    myEnableTestingFromSamples.setSelected(stepikSettings.isEnableTestingFromSamples());
    updateUsernameLabel(stepikSettings.getUser());
  }

  private void updateUsernameLabel(@Nullable StepicUser stepicUser) {
    if (stepicUser == null) {
      myUsernameLabel.setText("You're not logged in");
    }
    else {
      String firstName = stepicUser.getFirstName();
      String lastName = stepicUser.getLastName();
      String loggedInText = "You're logged in";
      if (firstName == null || lastName == null || firstName.isEmpty() || lastName.isEmpty()) {
        myUsernameLabel.setText(loggedInText);
      }
      else {
        myUsernameLabel.setText(loggedInText + " as " + firstName + " " + lastName);
      }
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
    boolean userDeleted = myStepicUser == null && user != null;
    boolean userModified = myStepicUser != null && !myStepicUser.equals(user);
    if (userDeleted || userModified) {
      stepikSettings.setUser(myStepicUser);
    }
    reset();
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

    boolean userDeleted = myStepicUser == null && user != null;
    boolean userModified = myStepicUser != null && !myStepicUser.equals(user);
    return isTestOptionModified || (userDeleted || userModified);
  }
}
