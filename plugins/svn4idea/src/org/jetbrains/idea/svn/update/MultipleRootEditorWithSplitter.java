/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.impl.VcsPathPresenter;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.AdjustComponentWhenShown;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.Map;

/**
 * @author irengrig
 */
public class MultipleRootEditorWithSplitter extends JPanel {
  private JList myList;
  private JPanel myConfigureRootPanel;
  @NonNls private static final String EMPTY = "empty";

  public MultipleRootEditorWithSplitter(final Map<FilePath, SvnPanel> rootToPanel, final Project project) {
    super(new BorderLayout());

    final Splitter splitter = new Splitter(false, 0.5f);
    splitter.setHonorComponentsMinimumSize(false);
    add(splitter, BorderLayout.CENTER);

    myList = new JBList();
    final Color borderColor = UIUtil.getBorderColor();
    myConfigureRootPanel = new JPanel();
    myConfigureRootPanel.setBorder(BorderFactory.createLineBorder(borderColor));
    splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myList));
    splitter.setSecondComponent(myConfigureRootPanel);

    final CardLayout layout = new CardLayout();
    myConfigureRootPanel.setLayout(layout);

    final DefaultListModel listModel = new DefaultListModel();

    layout.addLayoutComponent(new JPanel(), EMPTY);

    int minimumRightSize = 320;
    for (FilePath root : rootToPanel.keySet()) {
      final JPanel panel = rootToPanel.get(root).getPanel();
      myConfigureRootPanel.add(panel, root.getPath());
      if (panel.getMinimumSize().width > 0) {
        minimumRightSize = Math.max(minimumRightSize, panel.getPreferredSize().width);
      }
      listModel.addElement(root);
    }

    myConfigureRootPanel.revalidate();

    myList.setModel(listModel);

    myList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myList.setCellRenderer(new ColoredListCellRenderer(){
      protected void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
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

    myList.addListSelectionListener(e -> {
      final FilePath root = ((FilePath)myList.getSelectedValue());
      if (root != null) {
        layout.show(myConfigureRootPanel, root.getPath());
      } else {
        layout.show(myConfigureRootPanel, EMPTY);
      }
    });

    myList.setSelectedIndex(0);
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
      IdeFocusManager.getGlobalInstance().requestFocus(myList, true);
    });

    final int finalMinimumRightSize = minimumRightSize;
    new AdjustComponentWhenShown() {
      @Override
      protected boolean init() {
        if (getWidth() < finalMinimumRightSize * 2) {
          final int left = getWidth() - finalMinimumRightSize;
          final float newProportion;
          if (left < 0) {
            newProportion = 0.2f;
          } else {
            newProportion = ((float) left / getWidth());
          }
          splitter.setProportion(newProportion);
        }
        return true;
      }

      @Override
      protected boolean canExecute() {
        return getWidth() > 0;
      }
    }.install(splitter);
  }
}
