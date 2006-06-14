package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vcs.VcsConfiguration;

import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;

import org.tmatesoft.svn.core.SVNURL;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ImportOptionsDialog extends DialogWrapper implements ActionListener {

  private SVNURL myURL;
  private File myFile;
  private TextFieldWithBrowseButton myPathField;
  private JCheckBox myRecursiveCheckbox;
  private JCheckBox myIncludeIgnoredCheckbox;
  private JTextArea myCommitMessage;
  private Project myProject;

  public ImportOptionsDialog(Project project, SVNURL url, File target) {
    super(project, true);
    myURL = url;
    myFile = target;
    myProject = project;
    setTitle("SVN Import Options");
    init();
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "svn4idea.import.options";
  }

  public File getTarget() {
    return new File(myPathField.getText());
  }

  public boolean isRecursive() {
    return myRecursiveCheckbox.isSelected();
  }

  public boolean isIncludeIgnored() {
    return myIncludeIgnoredCheckbox.isSelected();
  }

  public String getCommitMessage() {
    return myCommitMessage.getText();
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

    panel.add(new JLabel("Import to:"), gc);
    gc.gridx += 1;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;
    JLabel urlLabel = new JLabel(myURL.toString());
    urlLabel.setFont(urlLabel.getFont().deriveFont(Font.BOLD));
    panel.add(urlLabel, gc);

    gc.gridy += 1;
    gc.gridwidth = 1;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    panel.add(new JLabel("Import from:"), gc);
    gc.gridx += 1;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;

    myPathField = new TextFieldWithBrowseButton(this);
    myPathField.setText(myFile.getAbsolutePath());
    myPathField.setEditable(false);
    panel.add(myPathField, gc);

    // other options.
    gc.gridy += 1;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.gridwidth = 3;
    gc.fill = GridBagConstraints.NONE;

    myRecursiveCheckbox = new JCheckBox("Import directories recursively");
    myRecursiveCheckbox.setSelected(true);
    panel.add(myRecursiveCheckbox, gc);
    gc.gridy += 1;
    myIncludeIgnoredCheckbox = new JCheckBox("Include ignored resources");
    myIncludeIgnoredCheckbox.setSelected(true);
    panel.add(myIncludeIgnoredCheckbox, gc);
    gc.gridy += 1;
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
    return myCommitMessage;
  }

  public void actionPerformed(ActionEvent e) {
    // choose directory here/
    FileChooserDescriptor fcd = new FileChooserDescriptor(false, true, false, false, false, false);
    fcd.setShowFileSystemRoots(true);
    fcd.setTitle("Checkout Directory");
    fcd.setDescription("Select directory to checkout from subversion");
    fcd.setHideIgnored(false);
    VirtualFile[] files = FileChooser.chooseFiles(getContentPane(), fcd, null);
    if (files == null || files.length != 1 || files[0] == null) {
      return;
    }
    myPathField.setText(files[0].getPath().replace('/', File.separatorChar));
  }
}
