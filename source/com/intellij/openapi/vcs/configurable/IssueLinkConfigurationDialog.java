package com.intellij.openapi.vcs.configurable;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.IssueNavigationLink;
import com.intellij.ui.DocumentAdapter;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * @author yole
 */
public class IssueLinkConfigurationDialog extends DialogWrapper {
  private JPanel myPanel;
  private JTextField myIssueIDTextField;
  private JTextField myIssueLinkTextField;
  private JLabel myErrorLabel;
  private JTextField myExampleIssueIDTextField;
  private JTextField myExampleIssueLinkTextField;

  protected IssueLinkConfigurationDialog(Project project) {
    super(project, false);
    init();
    myIssueIDTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(DocumentEvent e) {
        updateFeedback();
      }
    });
    myExampleIssueIDTextField.getDocument().addDocumentListener(new DocumentAdapter() {
      protected void textChanged(final DocumentEvent e) {
        updateFeedback();
      }
    });
  }

  private void updateFeedback() {
    myErrorLabel.setText(" ");
    try {
      Pattern p = Pattern.compile(myIssueIDTextField.getText());
      if (myIssueIDTextField.getText().length() > 0) {
        final Matcher matcher = p.matcher(myExampleIssueIDTextField.getText());
        if (matcher.matches()) {
          myExampleIssueLinkTextField.setText(matcher.replaceAll(myIssueLinkTextField.getText()));
        }
        else {
          myExampleIssueLinkTextField.setText("<no match>");
        }
      }
    }
    catch(Exception ex) {
      myErrorLabel.setText("Invalid regular expression: " + ex.getMessage());
      myExampleIssueLinkTextField.setText("");
    }
    setOKActionEnabled(myErrorLabel.getText().equals(" "));
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public IssueNavigationLink getLink() {
    return new IssueNavigationLink(myIssueIDTextField.getText(), myIssueLinkTextField.getText());
  }

  public void setLink(final IssueNavigationLink link) {
    myIssueIDTextField.setText(link.getIssueRegexp());
    myIssueLinkTextField.setText(link.getLinkRegexp());
  }

  public JComponent getPreferredFocusedComponent() {
    return myIssueIDTextField;
  }
}