// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn.dialogs;

import com.intellij.ide.DataManager;
import com.intellij.ide.actions.EditSourceAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MultiLineLabelUI;
import com.intellij.openapi.util.BooleanGetter;
import com.intellij.openapi.util.NlsContexts.TabTitle;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.ui.*;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.TreeUIHelper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;
import java.util.Objects;

import static com.intellij.openapi.vcs.changes.ChangesUtil.getNavigatableArray;
import static com.intellij.util.ContentsUtil.addContent;
import static com.intellij.util.containers.UtilKt.stream;
import static org.jetbrains.idea.svn.SvnBundle.message;

public final class IntersectingLocalChangesPanel {
  @NotNull private final BorderLayoutPanel myPanel;
  @NotNull private final List<? extends FilePath> myFiles;
  @NotNull private final Project myProject;

  public IntersectingLocalChangesPanel(@NotNull Project project, @NotNull List<? extends FilePath> files) {
    myProject = project;
    myFiles = files;
    myPanel = createPanel(createLabel(), createTree());
  }

  @NotNull
  private BorderLayoutPanel createPanel(@NotNull JLabel label, @NotNull JTree tree) {
    BorderLayoutPanel panel = JBUI.Panels.simplePanel();

    panel.setBackground(UIUtil.getTextFieldBackground());
    panel.addToTop(label).addToCenter(tree);
    new EditSourceAction().registerCustomShortcutSet(CommonShortcuts.getEditSource(), panel);

    DataManager.registerDataProvider(panel, dataId -> {
      if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) {
        return getNavigatableArray(myProject, stream(tree.getSelectionPaths())
          .map(TreePath::getLastPathComponent)
          .map(node -> (ChangesBrowserNode<?>)node)
          .flatMap(ChangesBrowserNode::getFilePathsUnderStream)
          .map(FilePath::getVirtualFile)
          .filter(Objects::nonNull)
          .distinct());
      }

      return null;
    });

    return panel;
  }

  @NotNull
  private SimpleTree createTree() {
    SimpleTree tree = new SimpleTree(TreeModelBuilder.buildFromFilePaths(myProject, NoneChangesGroupingFactory.INSTANCE, myFiles)) {
      @Override
      protected void configureUiHelper(@NotNull TreeUIHelper helper) {
        super.configureUiHelper(helper);
        helper.installEditSourceOnDoubleClick(this);
        helper.installEditSourceOnEnterKeyHandler(this);
      }
    };
    tree.setRootVisible(false);
    tree.setShowsRootHandles(false);
    tree.setCellRenderer(new ChangesBrowserNodeRenderer(myProject, BooleanGetter.TRUE, false));

    return tree;
  }

  @NotNull
  private static JBLabel createLabel() {
    JBLabel label = new JBLabel(message("label.merge.local.changes.intersection")) {
      @Override
      public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(size.width, (int)(size.height * 1.7));
      }
    };
    label.setUI(new MultiLineLabelUI());
    label.setBackground(UIUtil.getTextFieldBackground());
    label.setVerticalTextPosition(SwingConstants.TOP);

    return label;
  }

  public static void showInVersionControlToolWindow(@NotNull Project project,
                                                    @TabTitle @NotNull String title,
                                                    @NotNull List<? extends FilePath> files) {
    IntersectingLocalChangesPanel intersectingPanel = new IntersectingLocalChangesPanel(project, files);
    Content content = ContentFactory.SERVICE.getInstance().createContent(intersectingPanel.myPanel, title, true);
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ChangesViewContentManager.TOOLWINDOW_ID);

    addContent(toolWindow.getContentManager(), content, true);
    toolWindow.activate(null);
  }
}
