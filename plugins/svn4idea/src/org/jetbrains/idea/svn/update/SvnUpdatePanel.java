// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.idea.svn.DepthCombo;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;

import javax.swing.*;
import java.util.Collection;

public class SvnUpdatePanel extends AbstractSvnUpdatePanel {
  private JPanel myConfigureRootsPanel;
  private JCheckBox myForceBox;
  private JPanel myPanel;
  private DepthCombo myDepthCombo;
  private JLabel myDepthLabel;
  private JPanel myAdditionalPanel;
  private JCheckBox myIgnoreExternalsCheckBox;

  public SvnUpdatePanel(SvnVcs vcs, Collection<FilePath> roots) {
    super(vcs);
    init(roots);

    boolean descend = false;
    for (FilePath root : roots) {
      if (root.isDirectory()) {
        descend = true;
        break;
      }
    }
    myDepthCombo.setVisible(descend);
    myDepthLabel.setVisible(descend);
    myDepthLabel.setLabelFor(myDepthCombo);

    SvnConfiguration svnConfiguration = myVCS.getSvnConfiguration();
    myForceBox.setSelected(svnConfiguration.isForceUpdate());
    myIgnoreExternalsCheckBox.setSelected(svnConfiguration.isIgnoreExternals());
    myForceBox.addActionListener(e -> svnConfiguration.setForceUpdate(myForceBox.isSelected()));
    myIgnoreExternalsCheckBox.addActionListener(e -> svnConfiguration.setIgnoreExternals(myIgnoreExternalsCheckBox.isSelected()));
  }

  @Override
  protected JPanel getRootsPanel() {
    return myConfigureRootsPanel;
  }

  @Override
  protected JPanel getAdditionalPanel() {
    return myAdditionalPanel;
  }

  @Override
  protected SvnPanel createRootPanel(final FilePath root, final SvnVcs vcs, Collection<FilePath> roots) {
    return new SvnUpdateRootOptionsPanel(root, vcs, roots);
  }

  @Override
  protected JComponent getPanel() {
    return myPanel;
  }

  @Override
  protected DepthCombo getDepthBox() {
    return myDepthCombo;
  }

  private void createUIComponents() {
    myDepthCombo = new DepthCombo(true);
  }
}
