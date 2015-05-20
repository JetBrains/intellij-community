package com.jetbrains.edu.coursecreator.ui;

import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;

public class CCNewProjectPanel {
  private JPanel myPanel;
  private JTextArea myDescription;
  private JTextField myName;
  private JTextField myAuthorField;
  private FacetValidatorsManager myValidationManager;


  public CCNewProjectPanel() {
    final String userName = System.getProperty("user.name");
    if (userName != null) {
      myAuthorField.setText(userName);
    }
    myName.getDocument().addDocumentListener(new MyValidator());
    myDescription.getDocument().addDocumentListener(new MyValidator());
    myAuthorField.getDocument().addDocumentListener(new MyValidator());

    myDescription.setBorder(BorderFactory.createLineBorder(JBColor.border()));
  }

  public CCNewProjectPanel(String name, String author, String description) {
    this();
    myName.setText(name);
    myAuthorField.setText(author);
    myDescription.setText(description);
  }

  public JPanel getMainPanel() {
    return myPanel;
  }

  @NotNull
  public String getName() {
    return StringUtil.notNullize(myName.getText());
  }

  @NotNull
  public String getDescription() {
    return StringUtil.notNullize(myDescription.getText());
  }

  @NotNull
  public String[] getAuthors() {
    return StringUtil.splitByLines(StringUtil.notNullize(myAuthorField.getText()));
  }

  public void registerValidators(FacetValidatorsManager manager) {
    myValidationManager = manager;
  }

  private class MyValidator extends DocumentAdapter {

    @Override
    protected void textChanged(DocumentEvent e) {
      if (myValidationManager != null) {
        myValidationManager.validate();
      }
    }
  }

  public JTextField getAuthorField() {
    return myAuthorField;
  }

  public JTextArea getDescriptionField() {
    return myDescription;
  }

  public JTextField getNameField() {
    return myName;
  }
}
