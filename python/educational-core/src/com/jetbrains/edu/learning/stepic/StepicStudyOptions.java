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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.HoverHyperlinkLabel;
import com.intellij.ui.HyperlinkAdapter;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.jetbrains.edu.learning.StudySettings;
import com.jetbrains.edu.learning.settings.StudyOptionsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;

public class StepicStudyOptions implements StudyOptionsProvider {
  private JPanel myPane;
  private JBCheckBox myEnableTestingFromSamples;
  private JBLabel myUsernameLabel;
  private HoverHyperlinkLabel myHoverHyperlinkLabel;
  private StepicUser myStepicUser;
  private HyperlinkAdapter myListener;

  public StepicStudyOptions() {
  }

  @NotNull
  private HyperlinkAdapter createLogoutListener() {
    return new HyperlinkAdapter() {
      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        removeCredentials();
        updateLoginLabels(null);
      }
    };
  }

  @NotNull
  private HyperlinkAdapter createAuthorizeListener() {
    return new HyperlinkAdapter() {

      @Override
      protected void hyperlinkActivated(HyperlinkEvent e) {
        ApplicationManager.getApplication().getMessageBus().connect().subscribe(StudySettings.SETTINGS_CHANGED, () -> {
          StepicUser user = StudySettings.getInstance().getUser();
          if (user != null && !user.equals(myStepicUser)) {
            StudySettings.getInstance().setUser(myStepicUser);
            myStepicUser = user;
            updateLoginLabels(myStepicUser);
          }
        });

        EduStepicConnector.doAuthorize(() -> showDialog());
      }
    };
  }

  private void showDialog() {
    OAuthDialog dialog = new OAuthDialog();
    if (dialog.showAndGet()) {
      myStepicUser = dialog.getStepicUser();
      updateLoginLabels(myStepicUser);
    }
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
    updateLoginLabels(stepikSettings.getUser());
  }

  private void updateLoginLabels(@Nullable StepicUser stepicUser) {
    if (myListener != null) {
      myHoverHyperlinkLabel.removeHyperlinkListener(myListener);
    }

    if (stepicUser == null) {
      myUsernameLabel.setText("You're not logged in");
      myHoverHyperlinkLabel.setText("Log in to Stepik");

      myListener = createAuthorizeListener();
      myHoverHyperlinkLabel.addHyperlinkListener(myListener);
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

      myHoverHyperlinkLabel.setText("Log out");
      myListener = createLogoutListener();
      myHoverHyperlinkLabel.addHyperlinkListener(myListener);
    }
  }

  public void createUIComponents() {
    myHoverHyperlinkLabel = new HoverHyperlinkLabel("");
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

  private void removeCredentials() {
    myStepicUser = null;
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
