package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NonNls;
import org.tmatesoft.svn.core.SVNURL;

import javax.swing.*;
import java.io.File;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class ExportOptionsDialog extends DialogWrapper implements ActionListener {

  private SVNURL myURL;
  private File myFile;
  private TextFieldWithBrowseButton myPathField;
  private JCheckBox myRecursiveCheckbox;
  private JCheckBox myExternalsCheckbox;
  private JCheckBox myForceCheckbox;
  private JComboBox myEOLStyleBox;

  public ExportOptionsDialog(Project project, SVNURL url, File target) {
    super(project, true);
    myURL = url;
    myFile = target;
    setTitle("SVN Export Options");
    init();
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "svn4idea.export.options";
  }

  public File getTarget() {
    return new File(myPathField.getText());
  }

  public boolean isRecursive() {
    return myRecursiveCheckbox.isSelected();
  }

  public boolean isForce() {
    return myForceCheckbox.isSelected();
  }

  public boolean isIgnoreExternals() {
    return !myExternalsCheckbox.isSelected();
  }

  public String getEOLStyle() {
    if (myEOLStyleBox.getSelectedIndex() == 0) {
      return null;
    }
    return (String) myEOLStyleBox.getSelectedItem();
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

    panel.add(new JLabel("Export:"), gc);
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
    panel.add(new JLabel("Destination:"), gc);
    gc.gridx += 1;
    gc.gridwidth = 2;
    gc.weightx = 1;
    gc.fill = GridBagConstraints.HORIZONTAL;

    myPathField = new TextFieldWithBrowseButton(this);
    myPathField.setText(myFile.getAbsolutePath());
    myPathField.setEditable(false);
    panel.add(myPathField, gc);

    gc.gridy += 1;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.gridwidth = 3;
    gc.fill = GridBagConstraints.NONE;

    // other options.
    myRecursiveCheckbox = new JCheckBox("Export directories recursively");
    myRecursiveCheckbox.setSelected(true);
    panel.add(myRecursiveCheckbox, gc);
    gc.gridy += 1;
    myForceCheckbox = new JCheckBox("Replace existing files");
    myForceCheckbox.setSelected(true);
    panel.add(myForceCheckbox, gc);
    gc.gridy += 1;
    myExternalsCheckbox = new JCheckBox("Include externals locations");
    myExternalsCheckbox.setSelected(true);
    panel.add(myExternalsCheckbox, gc);
    gc.gridy += 1;
    gc.gridwidth = 2;
    panel.add(new JLabel("Override 'native' EOLs with:"), gc);
    gc.gridx += 2;
    gc.gridwidth = 1;
    myEOLStyleBox = new JComboBox(new Object[] {"None", "LF", "CRLF", "CR"});
    panel.add(myEOLStyleBox, gc);
    gc.gridy += 1;
    gc.gridwidth = 3;
    gc.gridx = 0;
    gc.weightx = 1;
    gc.weighty = 1;
    gc.anchor = GridBagConstraints.SOUTH;
    gc.fill = GridBagConstraints.HORIZONTAL;
    panel.add(new JSeparator(), gc);
    return panel;
  }

  public void actionPerformed(ActionEvent e) {
    // choose directory here/
    FileChooserDescriptor fcd = new FileChooserDescriptor(false, true, false, false, false, false);
    fcd.setShowFileSystemRoots(true);
    fcd.setTitle("Export Directory");
    fcd.setDescription("Select directory to export from subversion");
    fcd.setHideIgnored(false);
    VirtualFile[] files = FileChooser.chooseFiles(getContentPane(), fcd, null);
    if (files == null || files.length != 1 || files[0] == null) {
      return;
    }
    myPathField.setText(files[0].getPath().replace('/', File.separatorChar));
  }
}
