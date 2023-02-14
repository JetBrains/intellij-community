/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
  @Nullable
  protected JComponent createCenterPanel() {
    final JScrollPane pane = ScrollPaneFactory.createScrollPane(myTree);
    pane.setPreferredSize(JBUI.size(320, 400));
    return pane;
  }

  protected interface NodeSelector {
    boolean shouldSelect(SimpleNode node);
  }
}
