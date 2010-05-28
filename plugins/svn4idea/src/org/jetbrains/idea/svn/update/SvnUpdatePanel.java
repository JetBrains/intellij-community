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

import com.intellij.openapi.vcs.FilePath;
import com.intellij.ui.MultiLineTooltipUI;
import org.jetbrains.idea.svn.DepthCombo;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;

public class SvnUpdatePanel extends AbstractSvnUpdatePanel{
  private JPanel myConfigureRootsPanel;
  private JCheckBox myStatusBox;
  private JPanel myPanel;
  private JCheckBox myLockOnDemand;
  private DepthCombo myDepthCombo;
  private JLabel myDepthLabel;

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

    //final String updateOnDemandEnabled = System.getProperty("subversion.update.on.demand");
    //final boolean enable = "yes".equals(updateOnDemandEnabled);
    final boolean enable = true;

    myLockOnDemand.setVisible(enable);
    if (enable) {
      myLockOnDemand.setSelected(SvnConfiguration.getInstance(myVCS.getProject()).UPDATE_LOCK_ON_DEMAND);
      myLockOnDemand.addActionListener(new ActionListener() {
        public void actionPerformed(final ActionEvent e) {
          SvnConfiguration.getInstance(myVCS.getProject()).UPDATE_LOCK_ON_DEMAND = myLockOnDemand.isSelected();
        }
      });
    } else {
      // to be completely sure
      SvnConfiguration.getInstance(myVCS.getProject()).UPDATE_LOCK_ON_DEMAND = false;
    }
  }

  protected JPanel getRootsPanel() {
    return myConfigureRootsPanel;
  }

  protected SvnPanel createRootPanel(final FilePath root, final SvnVcs vcs, Collection<FilePath> roots) {
    return new SvnUpdateRootOptionsPanel(root, vcs, roots);
  }

  protected JComponent getPanel() {
    return myPanel;
  }

  protected JCheckBox getStatusBox() {
    return myStatusBox;
  }

  protected DepthCombo getDepthBox() {
    return myDepthCombo;
  }

  private void createUIComponents() {
    myLockOnDemand = new JCheckBox() {
      @Override
      public JToolTip createToolTip() {
        JToolTip toolTip = new JToolTip(){{
          setUI(new MultiLineTooltipUI());
        }};
        toolTip.setComponent(this);
        return toolTip;
      }
    };
  }
}
