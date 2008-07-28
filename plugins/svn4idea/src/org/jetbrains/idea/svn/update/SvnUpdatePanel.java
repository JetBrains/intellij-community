/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;

import javax.swing.*;
import java.util.Collection;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

public class SvnUpdatePanel extends AbstractSvnUpdatePanel{
  private JPanel myConfigureRootsPanel;
  private JCheckBox myStatusBox;
  private JCheckBox myRecursiveBox;
  private JPanel myPanel;
  private JCheckBox myLockOnDemand;

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
    myRecursiveBox.setVisible(descend);

    final String updateOnDemandEnabled = System.getProperty("subversion.update.on.demand");
    final boolean enable = "yes".equals(updateOnDemandEnabled);

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

  protected SvnPanel createRootPanel(final FilePath root, final SvnVcs vcs) {
    return new SvnUpdateRootOptionsPanel(root, vcs);
  }

  protected JComponent getPanel() {
    return myPanel;
  }

  protected JCheckBox getStatusBox() {
    return myStatusBox;
  }

  protected JCheckBox getRecursiveBox() {
    return myRecursiveBox;
  }
}
