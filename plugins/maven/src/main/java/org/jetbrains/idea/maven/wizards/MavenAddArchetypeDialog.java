/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.wizards;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.idea.maven.model.MavenArchetype;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MavenAddArchetypeDialog extends DialogWrapper {
  private JPanel myMainPanel;
  private JTextField myGroupIdField;
  private JTextField myArtifactIdField;
  private JTextField myVersionField;
  private JTextField myRepositoryField;

  public MavenAddArchetypeDialog(Component parent) {
    super(parent, false);
    setTitle("Add Archetype");

    init();

    DocumentAdapter l = new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        doValidateInput();
      }
    };

    myGroupIdField.getDocument().addDocumentListener(l);
    myArtifactIdField.getDocument().addDocumentListener(l);
    myVersionField.getDocument().addDocumentListener(l);
    myRepositoryField.getDocument().addDocumentListener(l);

    doValidateInput();
  }

  protected JComponent createCenterPanel() {
    return myMainPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myGroupIdField;
  }

  @Override
  protected String getHelpId() {
    return "Add_Archetype_Dialog";
  }

  private void doValidateInput() {
    List<String> errors = new ArrayList<>();
    if (StringUtil.isEmptyOrSpaces(myGroupIdField.getText())) errors.add("GroupId");
    if (StringUtil.isEmptyOrSpaces(myArtifactIdField.getText())) errors.add("ArtifactId");
    if (StringUtil.isEmptyOrSpaces(myVersionField.getText())) errors.add("Version");

    if (errors.isEmpty()) {
      setErrorText(null);
      getOKAction().setEnabled(true);
      return;
    }
    String message = "Please specify " + StringUtil.join(errors, ", ");
    setErrorText(message);
    getOKAction().setEnabled(false);
    getRootPane().revalidate();
  }

  public MavenArchetype getArchetype() {
    return new MavenArchetype(myGroupIdField.getText(),
                              myArtifactIdField.getText(),
                              myVersionField.getText(),
                              myRepositoryField.getText(),
                              null);
  }
}
