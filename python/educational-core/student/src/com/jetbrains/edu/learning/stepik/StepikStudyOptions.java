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
package com.jetbrains.edu.learning.stepik;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.jetbrains.edu.learning.StudyTaskManager;
import com.jetbrains.edu.learning.StudyUtils;
import com.jetbrains.edu.learning.settings.StudyOptionsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class StepikStudyOptions implements StudyOptionsProvider {
  private static final String DEFAULT_PASSWORD_TEXT = "************";
  private static final Logger LOG = Logger.getInstance(StepikStudyOptions.class);
  private JTextField myLoginTextField;
  private JPasswordField myPasswordField;
  private JPanel myPane;

  private boolean myCredentialsModified;

  public StepikStudyOptions() {
    myPasswordField.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        myCredentialsModified = true;
      }
    });

    DocumentListener passwordEraser = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        if (!myCredentialsModified) {
          erasePassword();
        }
      }
    };
    myLoginTextField.getDocument().addDocumentListener(passwordEraser);

    myPasswordField.addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        if (!myCredentialsModified && !getPassword().isEmpty()) {
          erasePassword();
        }
      }

      @Override
      public void focusLost(FocusEvent e) {
      }
    });
    reset();
  }

  private void erasePassword() {
    setPassword("");
    myCredentialsModified = true;
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
    myPasswordField.setText(StringUtil.isEmpty(password) ? null : password);
  }

  @Override
  public void reset() {
    Project project = StudyUtils.getStudyProject();
    if (project != null) {
      final StepikUser user = StudyTaskManager.getInstance(project).getUser();
      if (user != null) {
        setLogin(user.getEmail());
        setPassword(DEFAULT_PASSWORD_TEXT);
      }
      resetCredentialsModification();
    }
    else {
      LOG.warn("No study object is opened");
    }
  }

  @Override
  public void disposeUIResources() {

  }

  @Override
  public void apply() {
    if (myCredentialsModified) {
      final Project project = StudyUtils.getStudyProject();
      if (project != null) {
        final StepikUser user = StudyTaskManager.getInstance(project).getUser();
        user.setEmail(getLogin());
        user.setPassword(getPassword());
        if (!StringUtil.isEmptyOrSpaces(getLogin()) && !StringUtil.isEmptyOrSpaces(getPassword())) {
          StepikConnectorLogin.minorLogin(new StepikUser(getLogin(), getPassword()));
        }
      }
      else {
        LOG.warn("No study object is opened");
      }
    }
    resetCredentialsModification();
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myPane;
  }

  public boolean isModified() {
    return myCredentialsModified;
  }
  
  public void resetCredentialsModification() {
    myCredentialsModified = false;
  }

  private void createUIComponents() {
    Document doc = new PlainDocument();
    myPasswordField = new JPasswordField(doc, null, 0);
  }
}
