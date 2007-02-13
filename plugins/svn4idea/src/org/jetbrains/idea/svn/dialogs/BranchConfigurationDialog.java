/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.idea.svn.dialogs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBranchConfiguration;
import org.jetbrains.idea.svn.SvnBundle;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author yole
 */
public class BranchConfigurationDialog extends DialogWrapper {
  private JPanel myTopPanel;
  private TextFieldWithBrowseButton myTrunkLocationTextField;
  private JList myLocationList;
  private JButton myAddButton;
  private JButton myRemoveButton;

  public BranchConfigurationDialog(final Project project, final SvnBranchConfiguration configuration) {
    super(project, true);
    init();
    setTitle(SvnBundle.message("configure.branches.title"));

    myTrunkLocationTextField.setText(configuration.getTrunkUrl());
    myTrunkLocationTextField.addActionListener(new ActionListener() {
      public void actionPerformed(final ActionEvent e) {
        SelectLocationDialog dlg = new SelectLocationDialog(project, configuration.getTrunkUrl());
        dlg.show();
        if (dlg.isOK()) {
          myTrunkLocationTextField.setText(dlg.getSelectedURL());
        }
      }
    });

    final MyListModel listModel = new MyListModel(configuration);
    myLocationList.setModel(listModel);
    myAddButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        SelectLocationDialog dlg = new SelectLocationDialog(project, configuration.getTrunkUrl());
        dlg.show();
        if (dlg.isOK()) {
          if (!configuration.getBranchUrls().contains(dlg.getSelectedURL())) {
            configuration.getBranchUrls().add(dlg.getSelectedURL());
            listModel.fireItemAdded();
            myLocationList.setSelectedIndex(listModel.getSize()-1);
          }
        }
      }
    });
    myRemoveButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        int selIndex = myLocationList.getSelectedIndex();
        Object[] selection = myLocationList.getSelectedValues();
        for(Object urlObj: selection) {
          String url = (String) urlObj;
          int index = configuration.getBranchUrls().indexOf(url);
          configuration.getBranchUrls().remove(index);
          listModel.fireItemRemoved(index);
        }
        if (listModel.getSize() > 0) {
          if (selIndex >= listModel.getSize())
            selIndex = listModel.getSize()-1;
          myLocationList.setSelectedIndex(selIndex);
        }
      }
    });
  }

  @Nullable
  protected JComponent createCenterPanel() {
    return myTopPanel;
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "Subversion.BranchConfigurationDialog";
  }

  private static class MyListModel extends AbstractListModel {
    private final SvnBranchConfiguration myConfiguration;

    public MyListModel(final SvnBranchConfiguration configuration) {
      myConfiguration = configuration;
    }

    public int getSize() {
      return myConfiguration.getBranchUrls().size();
    }

    public Object getElementAt(final int index) {
      return myConfiguration.getBranchUrls().get(index);
    }

    public void fireItemAdded() {
      final int index = myConfiguration.getBranchUrls().size() - 1;
      super.fireIntervalAdded(this, index, index);
    }

    public void fireItemRemoved(final int index) {
      super.fireIntervalRemoved(this, index, index);
    }
  }
}