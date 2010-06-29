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
package com.intellij.application.options;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.roots.ui.configuration.actions.IconWithTextAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.Icons;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

public class TagListDialog extends DialogWrapper{
  private final JPanel myPanel = new JPanel(new BorderLayout());
  private final JList myList = new JBList(new DefaultListModel());
  private ArrayList<String> myData;

  public TagListDialog(String title) {
    super(true);
    myPanel.add(createToolbal(), BorderLayout.NORTH);
    myPanel.add(createList(), BorderLayout.CENTER);
    setTitle(title);
    init();
  }

  public void setData(ArrayList<String> data) {
    myData = data;
    updateData();
    if (!myData.isEmpty()) {
      myList.setSelectedIndex(0);
    }
  }

  private void updateData() {
    final DefaultListModel model = ((DefaultListModel)myList.getModel());
    model.clear();
    for (String data : myData) {
      model.addElement(data);
    }
  }

  public ArrayList<String> getData(){
    return myData;
  }

  private JComponent createList() {
    return new JBScrollPane(myList);
  }

  private JComponent createToolbal() {
    return ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN,
                                                           createActionGroup(),
                                                           true).getComponent();
  }

  private ActionGroup createActionGroup() {
    final DefaultActionGroup result = new DefaultActionGroup();
    final AnAction addAction = createAddAction();
    addAction.registerCustomShortcutSet(CommonShortcuts.INSERT, myList);
    result.add(addAction);

    final AnAction deleteAction = createDeleteAction();
    deleteAction.registerCustomShortcutSet(CommonShortcuts.DELETE, myList);
    result.add(deleteAction);
    return result;
  }

  private AnAction createDeleteAction() {
    return new IconWithTextAction(ApplicationBundle.message("action.remove"), null, Icons.DELETE_ICON) {
      public void update(AnActionEvent e) {
        final int selectedIndex = myList.getSelectedIndex();
        if (selectedIndex >= 0) {
          e.getPresentation().setEnabled(true);
        } else {
          e.getPresentation().setEnabled(false);
        }
      }

      public void actionPerformed(AnActionEvent e) {
        int selectedIndex = myList.getSelectedIndex();
        if (selectedIndex >= 0) {
          myData.remove(selectedIndex);
          updateData();
          if (selectedIndex >= myData.size()) {
            selectedIndex -= 1;
          }
          if (selectedIndex >= 0) {
            myList.setSelectedIndex(selectedIndex);
          }
        }
      }
    };
  }

  private AnAction createAddAction() {
    return new IconWithTextAction(ApplicationBundle.message("action.add"), null, Icons.ADD_ICON){
      public void actionPerformed(AnActionEvent e) {
        final String tagName = Messages.showInputDialog(ApplicationBundle.message("editbox.enter.tag.name"),
                                                        ApplicationBundle.message("title.tag.name"), Messages.getQuestionIcon());
        if (tagName != null) {
          while (myData.contains(tagName)) {
            myData.remove(tagName);
          }
          myData.add(tagName);
          updateData();
          myList.setSelectedIndex(myData.size() - 1);
        }
      }
    };
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public JComponent getPreferredFocusedComponent() {
    return myList;
  }
}
