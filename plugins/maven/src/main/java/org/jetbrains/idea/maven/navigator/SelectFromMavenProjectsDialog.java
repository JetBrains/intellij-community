// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.navigator.structure.MavenProjectsStructure;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

public class SelectFromMavenProjectsDialog extends DialogWrapper {
  private final SimpleTree myTree;
  private final NodeSelector mySelector;

  public SelectFromMavenProjectsDialog(Project project,
                                       @NlsContexts.DialogTitle String title,
                                       MavenProjectsStructure.MavenStructureDisplayMode displayMode) {
    this(project, title, displayMode, null);
  }

  public SelectFromMavenProjectsDialog(Project project,
                                       @NlsContexts.DialogTitle String title,
                                       MavenProjectsStructure.MavenStructureDisplayMode displayMode,
                                       @Nullable NodeSelector selector) {
    super(project, false);
    mySelector = selector;
    setTitle(title);

    myTree = new SimpleTree();
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

    MavenProjectsStructure treeStructure = new MavenProjectsStructure(project,
                                                                      displayMode,
                                                                      MavenProjectsManager.getInstance(project),
                                                                      MavenTasksManager.getInstance(project),
                                                                      MavenShortcutsManager.getInstance(project),
                                                                      MavenProjectsNavigator.getInstance(project),
                                                                      myTree);
    treeStructure.update();

    if (mySelector != null) {
      final SimpleNode[] selection = new SimpleNode[]{null};
      treeStructure.accept(new TreeVisitor() {
        @Override
        public @NotNull Action visit(@NotNull TreePath path) {
          SimpleNode node = (SimpleNode)((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
          if (!mySelector.shouldSelect(node)) return Action.CONTINUE;
          selection[0] = node;
          return Action.INTERRUPT;
        }
      });
      if (selection[0] != null) {
        treeStructure.select(selection[0]);
      }
    }

    init();
  }

  protected SimpleNode getSelectedNode() {
    return myTree.getNodeFor(myTree.getSelectionPath());
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    final JScrollPane pane = ScrollPaneFactory.createScrollPane(myTree);
    pane.setPreferredSize(JBUI.size(320, 400));
    return pane;
  }

  protected interface NodeSelector {
    boolean shouldSelect(SimpleNode node);
  }
}
