package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.vcs.checkout.CheckoutStrategy;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.DepthCombo;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.revision.SvnSelectRevisionPanel;
import org.jetbrains.idea.svn.update.SvnRevisionPanel;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.wc.SVNRevision;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CheckoutOptionsDialog extends DialogWrapper {
  private JCheckBox myExternalsCheckbox;
  private JLabel myUrlLabel;
  private JPanel myTopPanel;
  private SvnSelectRevisionPanel svnSelectRevisionPanel;
  private DepthCombo myDepthCombo;
  private JLabel myDepthLabel;
  private JList myLocalTargetList;
  private FixedSizeButton mySelectTarget;
  private final String myRelativePath;

  public CheckoutOptionsDialog(Project project, SVNURL url, File target, final VirtualFile root, final String relativePath) {
    super(project, true);
    myRelativePath = relativePath;
    final String urlText = url.toString();
    myUrlLabel.setText(urlText);

    fillTargetList(target);
    validateTargetSelected();

    mySelectTarget.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
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
        fillTargetList(new File(files[0].getPath()));
        validateTargetSelected();
      }
    });
    myLocalTargetList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(final ListSelectionEvent e) {
        validateTargetSelected();
      }
    });

    svnSelectRevisionPanel.setRoot(root);
    svnSelectRevisionPanel.setProject(project);
    svnSelectRevisionPanel.setUrlProvider(new SvnRevisionPanel.UrlProvider() {
      public String getUrl() {
        return urlText;
      }
    });

    setTitle(SvnBundle.message("checkout.options.dialog.title"));
    myDepthLabel.setLabelFor(myDepthCombo);
    init();
  }

  private void validateTargetSelected() {
    Object[] objects = myLocalTargetList.getSelectedValues();
    boolean disable = objects == null || objects.length != 1;
    setOKActionEnabled(! disable);
  }

  private void fillTargetList(final File target) {
    final DefaultListModel listModel = new DefaultListModel();
    final CheckoutStrategy[] strategies = CheckoutStrategy.createAllStrategies(target, new File(myRelativePath), false);
    final List<File> targets = new ArrayList<File>(4);
    for (CheckoutStrategy strategy : strategies) {
      final File result = strategy.getResult();
      if (result != null && (! targets.contains(result))) {
        targets.add(result);
      }
    }
    Collections.sort(targets);
    for (File file : targets) {
      listModel.addElement(file);
    }
    myLocalTargetList.setModel(listModel);
    myLocalTargetList.setVisibleRowCount(4);
    myLocalTargetList.setMinimumSize(new Dimension(20, 80));
    myLocalTargetList.setSelectedValue(target, true);
    if (myLocalTargetList.getSelectedValues() == null && (! targets.isEmpty())) {
      myLocalTargetList.setSelectedIndex(0);
    }
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "svn4idea.checkout.options";
  }

  @Nullable
  public File getTarget() {
    Object[] objects = myLocalTargetList.getSelectedValues();
    return (objects == null) || (objects.length != 1) ? null : (File) objects[0];
  }

  public SVNDepth getDepth() {
    return myDepthCombo.getSelectedItem();
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

  private void createUIComponents() {
    mySelectTarget = new FixedSizeButton(20); 
  }
}
