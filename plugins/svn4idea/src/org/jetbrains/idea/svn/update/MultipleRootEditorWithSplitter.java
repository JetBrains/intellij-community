// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.svn.update;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.impl.VcsPathPresenter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.dsl.listCellRenderer.BuilderKt;
import com.intellij.util.ui.AdjustComponentWhenShown;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

import static com.intellij.openapi.util.io.FileUtil.join;

public class MultipleRootEditorWithSplitter extends JPanel {
  private final JList<FilePath> myList;
  private final JPanel myConfigureRootPanel;
  private static final @NonNls String EMPTY = "empty";

  public MultipleRootEditorWithSplitter(final Map<FilePath, SvnPanel> rootToPanel, final Project project) {
    super(new BorderLayout());

    final Splitter splitter = new Splitter(false, 0.5f);
    splitter.setHonorComponentsMinimumSize(false);
    add(splitter, BorderLayout.CENTER);

    myList = new JBList<>();
    final Color borderColor = JBColor.border();
    myConfigureRootPanel = new JPanel();
    myConfigureRootPanel.setBorder(BorderFactory.createLineBorder(borderColor));
    splitter.setFirstComponent(ScrollPaneFactory.createScrollPane(myList));
    splitter.setSecondComponent(myConfigureRootPanel);

    final CardLayout layout = new CardLayout();
    myConfigureRootPanel.setLayout(layout);

    DefaultListModel<FilePath> listModel = new DefaultListModel<>();

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

    myList.setCellRenderer(BuilderKt.textListCellRenderer(o -> {
      VcsPathPresenter presenter = VcsPathPresenter.getInstance(project);
      VirtualFile file = o.getVirtualFile();
      return file != null
             ? presenter.getPresentableRelativePathFor(file)
             : join(presenter.getPresentableRelativePathFor(o.getVirtualFileParent()), o.getName());
    }));
    myList.addListSelectionListener(e -> {
      FilePath root = myList.getSelectedValue();
      if (root != null) {
        layout.show(myConfigureRootPanel, root.getPath());
      }
      else {
        layout.show(myConfigureRootPanel, EMPTY);
      }
    });

    myList.setSelectedIndex(0);
    IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myList, true));

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
