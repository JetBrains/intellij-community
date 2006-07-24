package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vcs.VcsConfiguration;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserComponent;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;

public class CopyOptionsDialog extends DialogWrapper {

  private SVNURL myURL;
  private JTextArea myCommitMessage;
  private Project myProject;
  private JTextField myNameField;
  private JLabel myURLLabel;
  private RepositoryBrowserComponent myBrowser;
  private SVNURL myRootURL;
  private JLabel myTargetURL;

  public CopyOptionsDialog(String title, Project project, SVNURL rootURL, SVNURL url) {
    super(project, true);
    myURL = url;
    myRootURL = rootURL;
    myProject = project;
    setTitle(title);
    init();
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "svn4idea.copy.options";
  }

  public String getCommitMessage() {
    return myCommitMessage.getText();
  }

  public SVNURL getSourceURL() {
    return myURL;
  }

  public SVNURL getTargetURL() {
    if (getOKAction().isEnabled()) {
      try {
        return SVNURL.parseURIEncoded(myTargetURL.getText());
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

    panel.add(new JLabel("Source URL:"), gc);
    gc.gridx += 1;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    myURLLabel = new JLabel(myURL.toString());
    myURLLabel.setFont(myURLLabel.getFont().deriveFont(Font.BOLD));
    panel.add(myURLLabel, gc);

    gc.gridy += 1;
    gc.gridwidth = 3;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    panel.add(new JLabel("Target Location:"), gc);
    gc.gridy += 1;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.weighty = 0.5;
    gc.gridwidth = 3;
    gc.fill = GridBagConstraints.BOTH;

    myBrowser = new RepositoryBrowserComponent(SvnVcs.getInstance(myProject));
    myBrowser.setRepositoryURL(myRootURL, false);
    myBrowser.addChangeListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        update();
      }
    });
    panel.add(myBrowser, gc);

    gc.gridy += 1;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.weighty = 0;
    gc.gridwidth = 1;
    gc.fill = GridBagConstraints.NONE;
    panel.add(new JLabel("Target Name:"), gc);
    gc.gridx += 1;
    gc.weightx = 1;
    gc.gridwidth = 2;
    gc.fill = GridBagConstraints.HORIZONTAL;
    myNameField = new JTextField();
    myNameField.setText(SVNPathUtil.tail(myURL.getPath()));
    myNameField.selectAll();
    myNameField.getDocument().addDocumentListener(new DocumentListener() {
      public void insertUpdate(DocumentEvent e) {
        update();
      }
      public void removeUpdate(DocumentEvent e) {
        update();
      }
      public void changedUpdate(DocumentEvent e) {
        update();
      }
    });
    panel.add(myNameField, gc);
    gc.gridy += 1;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.gridwidth = 1;
    gc.fill = GridBagConstraints.NONE;
    panel.add(new JLabel("Target URL: "), gc);
    gc.gridx += 1;
    gc.weightx = 1;
    gc.gridwidth = 2;
    gc.fill = GridBagConstraints.HORIZONTAL;
    myTargetURL = new JLabel();
    myTargetURL.setFont(myTargetURL.getFont().deriveFont(Font.BOLD));
    panel.add(myTargetURL, gc);

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
    gc.weighty = .5;
    gc.anchor = GridBagConstraints.NORTH;
    gc.fill = GridBagConstraints.BOTH;

    myCommitMessage = new JTextArea(5, 0);
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
    if (messages != null) {
      Collections.reverse(messages);
    }
    Object[] model = messages != null ? messages.toArray() : new Object[] {""};
    final JComboBox messagesBox = new JComboBox(model);
    messagesBox.setRenderer(new MessageBoxCellRenderer());
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
    update();
    return panel;
  }

  private void update() {
    RepositoryTreeNode baseNode = myBrowser.getSelectedNode();
    if (baseNode == null) {
      getOKAction().setEnabled(false);
      return;
    }
    SVNURL baseURL = baseNode.getURL();
    String name = myNameField.getText();
    if (name == null || "".equals(name)) {
      getOKAction().setEnabled(false);
      return;
    }
    try {
      baseURL = baseURL.appendPath(myNameField.getText(), false);
    } catch (SVNException e) {
      //
      getOKAction().setEnabled(false);
      return;
    }
    myTargetURL.setText(baseURL.toString());
    getOKAction().setEnabled(!myURL.toString().equals(myTargetURL.getText()));
  }


  public JComponent getPreferredFocusedComponent() {
    return myNameField;
  }

}
