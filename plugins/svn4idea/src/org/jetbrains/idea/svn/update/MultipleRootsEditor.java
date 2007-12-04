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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.impl.VcsPathPresenter;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.io.File;
import java.util.Map;

public class MultipleRootsEditor {
  private JList myList;
  private JPanel myConfigureRootPanel;
  private JPanel myPanel;
  @NonNls private static final String EMPTY = "empty";

  public MultipleRootsEditor(final Map<FilePath, SvnPanel> rootToPanel, final Project project) {

    final CardLayout layout = new CardLayout();
    myConfigureRootPanel.setLayout(layout);

    final DefaultListModel listModel = new DefaultListModel();

    layout.addLayoutComponent(new JPanel(), EMPTY);

    for (FilePath root : rootToPanel.keySet()) {
      myConfigureRootPanel.add(rootToPanel.get(root).getPanel(), root.getPath());
      listModel.addElement(root);
    }

    myConfigureRootPanel.revalidate();

    myList.setModel(listModel);

    myList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myList.setCellRenderer(new ColoredListCellRenderer(){
      protected void customizeCellRenderer(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        if (value instanceof FilePath) {
          final FilePath path = ((FilePath)value);
          if (path.getVirtualFile() != null) {
            append(VcsPathPresenter.getInstance(project).getPresentableRelativePathFor(path.getVirtualFile()),
                   SimpleTextAttributes.REGULAR_ATTRIBUTES);
          } else {
            append(VcsPathPresenter.getInstance(project).getPresentableRelativePathFor(path.getVirtualFileParent()) + File.separator + path.getName(),
                   SimpleTextAttributes.REGULAR_ATTRIBUTES);

          }
        }
      }
    });

    myList.addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        final FilePath root = ((FilePath)myList.getSelectedValue());
        if (root != null) {
          layout.show(myConfigureRootPanel, root.getPath());
        } else {
          layout.show(myConfigureRootPanel, EMPTY);
        }
      }
    });

    myList.setSelectedIndex(0);

    myList.requestFocus();

  }

  public JPanel getPanel() {
    return myPanel;
  }
}
