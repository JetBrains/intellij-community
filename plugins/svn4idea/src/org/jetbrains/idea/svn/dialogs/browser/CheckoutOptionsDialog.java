package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.options.ConfigurationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.revision.SvnSelectRevisionPanel;
import org.jetbrains.idea.svn.update.SvnRevisionPanel;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

public class CheckoutOptionsDialog extends DialogWrapper implements ActionListener {
  private TextFieldWithBrowseButton myPathField;
  private JCheckBox myRecursiveCheckbox;
  private JCheckBox myExternalsCheckbox;
  private JLabel myUrlLabel;
  private JPanel myTopPanel;
  private SvnSelectRevisionPanel svnSelectRevisionPanel;

  public CheckoutOptionsDialog(Project project, SVNURL url, File target) {
    super(project, true);
    final String urlText = url.toString();
    myUrlLabel.setText(urlText);
    myPathField.setText(target.getAbsolutePath());
    myPathField.addActionListener(this);

    svnSelectRevisionPanel.setProject(project);
    svnSelectRevisionPanel.setUrlProvider(new SvnRevisionPanel.UrlProvider() {
      public String getUrl() {
        return urlText;
      }
    });

    setTitle(SvnBundle.message("checkout.options.dialog.title"));
    init();
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "svn4idea.checkout.options";
  }

  public File getTarget() {
    return new File(myPathField.getText());
  }

  public boolean isRecursive() {
    return myRecursiveCheckbox.isSelected();
  }

  public boolean isIgnoreExternals() {
    return !myExternalsCheckbox.isSelected();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myTopPanel;
  }

  @NotNull
  public SVNRevision getRevision() throws ConfigurationException {
      return svnSelectRevisionPanel.getRevision();
  }

  public void actionPerformed(ActionEvent e) {
    // choose directory here/
    FileChooserDescriptor fcd = new FileChooserDescriptor(false, true, false, false, false, false);
    fcd.setShowFileSystemRoots(true);
    fcd.setTitle(SvnBundle.message("checkout.directory.chooser.title"));
    fcd.setDescription(SvnBundle.message("checkout.directory.chooser.prompt"));
    fcd.setHideIgnored(false);
    VirtualFile[] files = FileChooser.chooseFiles(getContentPane(), fcd, null);
    if (files.length != 1 || files[0] == null) {
      return;
    }
    myPathField.setText(files[0].getPath().replace('/', File.separatorChar));
  }
}
