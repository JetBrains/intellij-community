/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.AnActionButtonRunnable;
import com.intellij.ui.ToolbarDecorator;
import com.intellij.ui.components.JBList;

import javax.swing.*;
import java.util.ArrayList;

public class TagListDialog extends DialogWrapper {
  private final JPanel myPanel;
  private final JList myList = new JBList(new DefaultListModel());
  private ArrayList<String> myData;

  public TagListDialog(String title) {
    super(true);
    myPanel = ToolbarDecorator.createDecorator(myList)
      .setAddAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
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
      }).setRemoveAction(new AnActionButtonRunnable() {
        @Override
        public void run(AnActionButton button) {
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
      }).disableUpDownActions().createPanel();
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

  public ArrayList<String> getData() {
    return myData;
  }

  @Override
  protected JComponent createCenterPanel() {
    return myPanel;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myList;
  }
}
