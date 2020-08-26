// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.wizards;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArchetype;
import org.jetbrains.idea.maven.project.MavenConfigurableBundle;
import org.jetbrains.idea.maven.project.MavenProjectBundle;

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
    setTitle(MavenConfigurableBundle.message("maven.settings.archetype.add.title"));

    init();

    DocumentAdapter l = new DocumentAdapter() {
      @Override
      protected void textChanged(@NotNull DocumentEvent e) {
        doValidateInput();
      }
    };

    myGroupIdField.getDocument().addDocumentListener(l);
    myArtifactIdField.getDocument().addDocumentListener(l);
    myVersionField.getDocument().addDocumentListener(l);
    myRepositoryField.getDocument().addDocumentListener(l);

    doValidateInput();
  }

  @Override
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
    String message = MavenProjectBundle.message("dialog.message.please.specify", StringUtil.join(errors, ", "));
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
