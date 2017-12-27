// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs.browser;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.FixedSizeButton;
import com.intellij.openapi.vcs.checkout.CheckoutStrategy;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.DepthCombo;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.api.Depth;
import org.jetbrains.idea.svn.api.Revision;
import org.jetbrains.idea.svn.api.Url;
import org.jetbrains.idea.svn.revision.SvnSelectRevisionPanel;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.vfs.VfsUtilCore.virtualToIoFile;

public class CheckoutOptionsDialog extends DialogWrapper {
  private JCheckBox myExternalsCheckbox;
  private JLabel myUrlLabel;
  private JPanel myTopPanel;
  private SvnSelectRevisionPanel svnSelectRevisionPanel;
  private DepthCombo myDepthCombo;
  private JLabel myDepthLabel;
  private JList myLocalTargetList;
  private FixedSizeButton mySelectTarget;
  private JBScrollPane myScroll;
  private final String myRelativePath;

  public CheckoutOptionsDialog(final Project project, Url url, File target, final VirtualFile root, final String relativePath) {
    super(project, true);
    myRelativePath = relativePath;
    myUrlLabel.setText(url.toDecodedString());

    fillTargetList(target);
    validateTargetSelected();

    mySelectTarget.addActionListener(e -> {
      // choose directory here/
      FileChooserDescriptor fcd = FileChooserDescriptorFactory.createSingleFolderDescriptor();
      fcd.setShowFileSystemRoots(true);
      fcd.setTitle(SvnBundle.message("checkout.directory.chooser.title"));
      fcd.setDescription(SvnBundle.message("checkout.directory.chooser.prompt"));
      fcd.setHideIgnored(false);
      VirtualFile file = FileChooser.chooseFile(fcd, getContentPane(), project, null);
      if (file == null) {
        return;
      }
      fillTargetList(virtualToIoFile(file));
      validateTargetSelected();
    });
    myLocalTargetList.addListSelectionListener(e -> validateTargetSelected());

    svnSelectRevisionPanel.setRoot(root);
    svnSelectRevisionPanel.setProject(project);
    svnSelectRevisionPanel.setUrlProvider(() -> url);

    setTitle(SvnBundle.message("checkout.options.dialog.title"));
    myDepthLabel.setLabelFor(myDepthCombo);
    init();
    myScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
  }

  private void validateTargetSelected() {
    Object[] objects = myLocalTargetList.getSelectedValues();
    boolean disable = objects == null || objects.length != 1;
    setOKActionEnabled(! disable);
  }

  private void fillTargetList(final File target) {
    final DefaultListModel listModel = new DefaultListModel();
    final List<CheckoutStrategy> strategies = new ArrayList<>();
    Collections.addAll(strategies, CheckoutStrategy.createAllStrategies(target, new File(myRelativePath), false));
    strategies.add(new SvnTrunkCheckoutStrategy(target, new File(myRelativePath), false));
    final List<File> targets = new ArrayList<>(5);
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
    myLocalTargetList.setPreferredSize(new Dimension(20, 80));
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

  public Depth getDepth() {
    return myDepthCombo.getDepth();
  }

  public boolean isIgnoreExternals() {
    return !myExternalsCheckbox.isSelected();
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myTopPanel;
  }

  @NotNull
  public Revision getRevision() throws ConfigurationException {
      return svnSelectRevisionPanel.getRevision();
  }

  private void createUIComponents() {
    mySelectTarget = new FixedSizeButton(20);
    myDepthCombo = new DepthCombo(false);
  }
}
