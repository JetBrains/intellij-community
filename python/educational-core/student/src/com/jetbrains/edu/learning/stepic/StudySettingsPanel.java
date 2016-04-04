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

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.jetbrains.edu.stepic.EduStepicConnector;
import com.jetbrains.edu.stepic.StudySettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

public class StudySettingsPanel {
  private static final String DEFAULT_PASSWORD_TEXT = "************";
  private JTextField myLoginTextField;
  private JPasswordField myPasswordField;
  private JPanel myPane;
  private JPanel myCardPanel;

  private boolean myCredentialsModified;

  public StudySettingsPanel() {
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

  public void reset() {
    final StudySettings studySettings = StudySettings.getInstance();
    setLogin(studySettings.getLogin());
    setPassword(DEFAULT_PASSWORD_TEXT);

    resetCredentialsModification();
  }

  public void apply() {
    if (myCredentialsModified) {
      final StudySettings studySettings = StudySettings.getInstance();
      studySettings.setLogin(getLogin());
      studySettings.setPassword(getPassword());
      if (!StringUtil.isEmptyOrSpaces(getLogin()) && !StringUtil.isEmptyOrSpaces(getPassword())) {
        EduStepicConnector.login(getLogin(), getPassword());
      }
    }
    resetCredentialsModification();
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
