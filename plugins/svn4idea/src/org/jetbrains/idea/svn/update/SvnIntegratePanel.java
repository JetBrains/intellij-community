/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  protected SvnPanel createRootPanel(final FilePath root, final SvnVcs p1, Collection<FilePath> roots) {
    return new SvnIntegrateRootOptionsPanel(myVCS, root);
  }

  protected JPanel getRootsPanel() {
    return myRootOptionsPanel;
  }

  @Override
  protected JPanel getAdditionalPanel() {
    return myAdditionalPanel;
  }

  public void reset(final SvnConfiguration configuration) {
    super.reset(configuration);
    myDryRunCheckbox.setSelected(configuration.isMergeDryRun());
    myUseAncestry.setSelected(configuration.isMergeDiffUseAncestry());
  }
  public void apply(final SvnConfiguration configuration) throws ConfigurationException {
    super.apply(configuration);
    configuration.setMergeDryRun(myDryRunCheckbox.isSelected());
    configuration.setMergeDiffUseAncestry(myUseAncestry.isSelected());
  }

  protected JComponent getPanel() {
    return myPanel;
  }

  protected DepthCombo getDepthBox() {
    return myDepthCombo;
  }

  private void createUIComponents() {
    myDepthCombo = new DepthCombo(true);
  }
}
