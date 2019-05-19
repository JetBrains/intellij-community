// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.idea.svn.DepthCombo;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;

import javax.swing.*;
import java.util.Collection;

public class SvnIntegratePanel extends AbstractSvnUpdatePanel{

  private JCheckBox myDryRunCheckbox;
  private JPanel myRootOptionsPanel;
  private JPanel myPanel;
  private JCheckBox myUseAncestry;
  private DepthCombo myDepthCombo;
  private JLabel myDepthLabel;
  private JPanel myAdditionalPanel;

  public SvnIntegratePanel(final SvnVcs vcs, Collection<FilePath> roots) {
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
  }

  @Override
  protected SvnPanel createRootPanel(final FilePath root, final SvnVcs p1, Collection<FilePath> roots) {
    return new SvnIntegrateRootOptionsPanel(myVCS, root);
  }

  @Override
  protected JPanel getRootsPanel() {
    return myRootOptionsPanel;
  }

  @Override
  protected JPanel getAdditionalPanel() {
    return myAdditionalPanel;
  }

  @Override
  public void reset(final SvnConfiguration configuration) {
    super.reset(configuration);
    myDryRunCheckbox.setSelected(configuration.isMergeDryRun());
    myUseAncestry.setSelected(configuration.isMergeDiffUseAncestry());
  }
  @Override
  public void apply(final SvnConfiguration configuration) throws ConfigurationException {
    super.apply(configuration);
    configuration.setMergeDryRun(myDryRunCheckbox.isSelected());
    configuration.setMergeDiffUseAncestry(myUseAncestry.isSelected());
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
