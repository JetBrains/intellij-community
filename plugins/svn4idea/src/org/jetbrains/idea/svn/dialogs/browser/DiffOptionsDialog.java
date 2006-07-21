/*
 * Created by IntelliJ IDEA.
 * User: Alexander.Kitaev
 * Date: 19.07.2006
 * Time: 16:51:09
 */
package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.RepositoryBrowserComponent;
import org.jetbrains.idea.svn.dialogs.RepositoryTreeNode;
import org.tmatesoft.svn.core.SVNURL;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class DiffOptionsDialog extends DialogWrapper implements ActionListener {

  private SVNURL myURL;
  private Project myProject;
  private RepositoryBrowserComponent myBrowser;
  private SVNURL myRootURL;

  private JRadioButton myUnifiedDiffButton;
  private JRadioButton myUIDiffButton;
  private ButtonGroup myButtonsGroup;
  private TextFieldWithBrowseButton myFileBrowser;
  private JCheckBox myReverseDiffButton;

  public DiffOptionsDialog(Project project, SVNURL rootURL, SVNURL url) {
    super(project, true);
    myURL = url;
    myRootURL = rootURL;
    myProject = project;
    setTitle("Compare With Branch or Tag");
    init();
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "svn4idea.diff.options";
  }

  public SVNURL getSourceURL() {
    return myURL;
  }

  public boolean isReverseDiff() {
    return myReverseDiffButton.isSelected();
  }

  public SVNURL getTargetURL() {
    if (getOKAction().isEnabled() && myBrowser.getSelectedNode() != null) {
        return myBrowser.getSelectedNode().getURL();
    }
    return null;
  }

  public File getTargetFile() {
    return new File(myFileBrowser.getText());
  }

  public boolean isUnifiedDiff() {
    return myUnifiedDiffButton.isSelected();
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
    panel.add(new JLabel("Compare:"), gc);
    gc.gridy += 1;
    gc.gridwidth = 3;
    JLabel sourceURL = new JLabel(myURL.toString());
    sourceURL.setFont(sourceURL.getFont().deriveFont(Font.BOLD));
    panel.add(sourceURL, gc);

    gc.gridy += 1;
    gc.gridwidth = 3;
    gc.gridx = 0;
    gc.weightx = 0;
    gc.fill = GridBagConstraints.NONE;
    panel.add(new JLabel("With:"), gc);
    gc.gridy += 1;
    gc.gridx = 0;
    gc.weightx = 1;
    gc.weighty = 1;
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
    gc.weighty = 0;
    gc.weightx = 0;
    myReverseDiffButton = new JCheckBox("Reverse Diff");
    panel.add(myReverseDiffButton, gc);

    myUIDiffButton = new JRadioButton("Graphical Compare");
    myUnifiedDiffButton = new JRadioButton("Unified Diff");
    myButtonsGroup = new ButtonGroup();
    myButtonsGroup.add(myUIDiffButton);
    myButtonsGroup.add(myUnifiedDiffButton);


    JPanel buttonsPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gc2 = new GridBagConstraints();
    gc2.insets = new Insets(2, 2, 2, 2);
    gc2.gridwidth = 3;
    gc2.gridheight = 1;
    gc2.gridx = 0;
    gc2.gridy = 0;
    gc2.weightx = 1;
    gc2.anchor = GridBagConstraints.WEST;
    gc2.fill = GridBagConstraints.HORIZONTAL;

    buttonsPanel.add(myUIDiffButton, gc2);
    gc2.gridy += 1;
    buttonsPanel.add(myUnifiedDiffButton, gc2);
    myUIDiffButton.addActionListener(this);
    myUnifiedDiffButton.addActionListener(this);
    myUIDiffButton.setSelected(true);

    myFileBrowser = new TextFieldWithBrowseButton(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        File f = selectFile("Patch File", "Select file to store unified diff");
        if (f != null) {
          if (f.exists() && f.isDirectory()) {
            f = new File(f, "diff.txt");
          }
          myFileBrowser.setText(f.getAbsolutePath());
        }
      }
    });
    myFileBrowser.setEnabled(false);

    String projectDir = myProject.getProjectFile().getParent().getPath();
    projectDir = projectDir.replace('/', File.separatorChar);
    myFileBrowser.setText(projectDir + File.separatorChar + "diff.txt");

    gc2.gridy += 1;
    buttonsPanel.add(myFileBrowser, gc2);
    buttonsPanel.setBorder(new TitledBorder("Compare Type"));

    gc.gridy += 1;
    gc.weightx = 0;
    gc.weighty = 0;
    panel.add(buttonsPanel, gc);

    update();
    return panel;
  }

  private void update() {
    RepositoryTreeNode baseNode = myBrowser.getSelectedNode();
    if (baseNode == null) {
      getOKAction().setEnabled(false);
      return;
    }
    getOKAction().setEnabled(!myURL.equals(getTargetURL()));
  }

  public JComponent getPreferredFocusedComponent() {
    return myBrowser;
  }

  private File selectFile(String title, String description) {
    FileChooserDescriptor fcd = new FileChooserDescriptor(true, true, false, false, false, false);
    fcd.setShowFileSystemRoots(true);
    fcd.setTitle(title);
    fcd.setDescription(description);
    fcd.setHideIgnored(false);
    VirtualFile[] files = FileChooser.chooseFiles(myBrowser, fcd, null);
    if (files == null || files.length != 1 || files[0] == null) {
      return null;
    }
    return new File(files[0].getPath());
  }

  public void actionPerformed(ActionEvent e) {
    myFileBrowser.setEnabled(myUnifiedDiffButton.isSelected());
    update();
  }
}