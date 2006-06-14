package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsConfiguration;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

public class MkdirOptionsDialog extends DialogWrapper {

  private SVNURL myURL;
  private JTextArea myCommitMessage;
  private Project myProject;
  private JTextField myNameField;
  private JLabel myURLLabel;
  private SVNURL myOriginalURL;

  public MkdirOptionsDialog(Project project, SVNURL url) {
    super(project, true);
    myOriginalURL = url;
    try {
      myURL = url.appendPath("NewFolder", true);
    } catch (SVNException e) {
      //
    }
    myProject = project;
    setTitle("New Remote Folder");
    init();
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "svn4idea.mkdir.options";
  }

  public String getCommitMessage() {
    return myCommitMessage.getText();
  }

  public SVNURL getURL() {
    if (getOKAction().isEnabled()) {
      try {
        return SVNURL.parseURIEncoded(myURLLabel.getText());
      } catch (SVNException e) {
        //
      }
    }
    return null;
  }

  @Nullable
  protected JComponent createCenterPanel() {
    JPanel panel = new JPanel(new GridBagLayout());

    GridBagConstraints gc = new GridBagConstraints();
    gc.insets = new Insets(2, 2, 2, 2);
    gc.gridwidth = 1;
    gc.gridheight = 1;
    gc.gridx = 0;
    gc.gridy = 0;
    gc.anchor = GridBagConstraints.WEST;
    gc.fill = GridBagConstraints.NONE;
    gc.weightx = 0;
    gc.weighty = 0;

    panel.add(new JLabel("Remote Folder URL:"), gc);
    gc.gridx += 1;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    myURLLabel = new JLabel(myURL.toString());
    myURLLabel.setFont(myURLLabel.getFont().deriveFont(Font.BOLD));
    panel.add(myURLLabel, gc);

    gc.gridy += 1;
    gc.gridwidth = 1;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    panel.add(new JLabel("Remote Folder Name:"), gc);
    gc.gridx += 1;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;

    myNameField = new JTextField();
    myNameField.setText("NewFolder");
    myNameField.selectAll();
    panel.add(myNameField, gc);

    myNameField.getDocument().addDocumentListener(new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        updateURL();
      }
      public void removeUpdate(DocumentEvent e) {
        updateURL();
      }
      public void changedUpdate(DocumentEvent e) {
        updateURL();
      }
    });

    gc.gridy += 1;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.gridwidth = 3;
    gc.fill = GridBagConstraints.NONE;
    panel.add(new JLabel("Commit Message:"), gc);
    gc.gridy += 1;
    gc.gridwidth = 3;
    gc.gridx = 0;
    gc.weightx = 1;
    gc.weighty = 1;
    gc.anchor = GridBagConstraints.NORTH;
    gc.fill = GridBagConstraints.BOTH;

    myCommitMessage = new JTextArea(10, 0);
    panel.add(new JScrollPane(myCommitMessage), gc);

    gc.gridy += 1;
    gc.gridwidth = 3;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.weighty = 0;
    gc.anchor = GridBagConstraints.NORTH;
    gc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(new JLabel("Recent Messages: "), gc);
    gc.gridy += 1;

    ArrayList<String> messages = VcsConfiguration.getInstance(myProject).getRecentMessages();
    Object[] model = messages != null ? messages.toArray() : new Object[] {""};
    final JComboBox messagesBox = new JComboBox(model);
    panel.add(messagesBox, gc);

    String lastMessage = VcsConfiguration.getInstance(myProject).getLastNonEmptyCommitMessage();
    if (lastMessage != null) {
      myCommitMessage.setText(lastMessage);
      myCommitMessage.selectAll();
    }
    messagesBox.addActionListener(new ActionListener() {

      public void actionPerformed(ActionEvent e) {
        myCommitMessage.setText(messagesBox.getSelectedItem().toString());
        myCommitMessage.selectAll();
      }
    });
    return panel;
  }


  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

  private void updateURL() {
    String newName = myNameField.getText();
    if (newName == null || "".equals(newName)) {
      myURLLabel.setText(myOriginalURL.toString());
      getOKAction().setEnabled(false);
      return;
    }
    try {
      myURLLabel.setText(myOriginalURL.appendPath(newName, false).toString());
      getOKAction().setEnabled(true);
    } catch (SVNException e) {
      myURLLabel.setText(myOriginalURL.toString());
      getOKAction().setEnabled(false);
    }
  }
}
