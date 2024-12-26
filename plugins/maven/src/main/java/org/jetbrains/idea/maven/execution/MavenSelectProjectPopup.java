// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution;

import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.PopupChooserBuilder;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Consumer;
import icons.MavenIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenProjectNamer;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class MavenSelectProjectPopup {

  public static void attachToWorkingDirectoryField(final @NotNull MavenProjectsManager projectsManager,
                                                   final JTextField workingDirectoryField,
                                                   final JButton showModulesButton,
                                                   final @Nullable JComponent focusAfterSelection) {
    attachToButton(projectsManager, showModulesButton, project -> {
      workingDirectoryField.setText(project.getDirectory());

      if (focusAfterSelection != null) {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (workingDirectoryField.hasFocus()) {
            IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(focusAfterSelection, true));
          }
        });
      }
    });

    workingDirectoryField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_DOWN) {
          if (!e.isConsumed()) { // May be consumed by path completion.
            e.consume();
            showModulesButton.doClick();
          }
        }
      }
    });
  }

  public static void attachToButton(final @NotNull MavenProjectsManager projectsManager,
                                    final @NotNull JButton button,
                                    final @NotNull Consumer<? super MavenProject> callback) {
    button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        List<MavenProject> projectList = projectsManager.getProjects();
        if (projectList.isEmpty()) {
          JBPopupFactory.getInstance().createMessage(
            MavenProjectBundle.message("popup.content.maven.projects.not.found")).showUnderneathOf(button);
          return;
        }

        DefaultMutableTreeNode root = buildTree(projectList);

        final Map<MavenProject, String> projectsNameMap = MavenProjectNamer.generateNameMap(projectList);

        final Tree projectTree = new Tree(root);
        projectTree.setRootVisible(false);
        projectTree.setCellRenderer(new NodeRenderer() {
          @Override
          public void customizeCellRenderer(@NotNull JTree tree,
                                            Object value,
                                            boolean selected,
                                            boolean expanded,
                                            boolean leaf,
                                            int row,
                                            boolean hasFocus) {
            if (value instanceof DefaultMutableTreeNode) {
              MavenProject mavenProject = (MavenProject)((DefaultMutableTreeNode)value).getUserObject();
              value = projectsNameMap.get(mavenProject);
              setIcon(MavenIcons.MavenProject);
            }

            super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
          }
        });

        TreeSpeedSearch.installOn(projectTree, false, o -> {
          Object lastPathComponent = o.getLastPathComponent();
          if (!(lastPathComponent instanceof DefaultMutableTreeNode)) return null;

          Object userObject = ((DefaultMutableTreeNode)lastPathComponent).getUserObject();

          //noinspection SuspiciousMethodCalls
          return projectsNameMap.get(userObject);
        });

        final Ref<JBPopup> popupRef = new Ref<>();

        final Runnable clickCallBack = () -> {
          TreePath path = projectTree.getSelectionPath();
          if (path == null) return;

          Object lastPathComponent = path.getLastPathComponent();
          if (!(lastPathComponent instanceof DefaultMutableTreeNode)) return;

          Object object = ((DefaultMutableTreeNode)lastPathComponent).getUserObject();
          if (object == null) return; // may be it's the root

          callback.consume((MavenProject)object);

          popupRef.get().closeOk(null);
        };

        projectTree.addKeyListener(new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
              clickCallBack.run();
              e.consume();
            }
          }
        });

        JBPopup popup = new PopupChooserBuilder(projectTree)
          .setTitle(RunnerBundle.message("maven.select.project"))
          .setResizable(true)
          .setItemChosenCallback(clickCallBack).setAutoselectOnMouseMove(true)
          .setCloseOnEnter(false)
          .createPopup();

        popupRef.set(popup);

        popup.showUnderneathOf(button);
      }

      private DefaultMutableTreeNode buildTree(List<MavenProject> projectList) {
        MavenProject[] projects = projectList.toArray(new MavenProject[0]);
        Arrays.sort(projects, new MavenProjectNamer.MavenProjectComparator());

        Map<MavenProject, DefaultMutableTreeNode> projectsToNode = new HashMap<>();
        for (MavenProject mavenProject : projects) {
          projectsToNode.put(mavenProject, new DefaultMutableTreeNode(mavenProject));
        }

        DefaultMutableTreeNode root = new DefaultMutableTreeNode();

        for (MavenProject mavenProject : projects) {
          DefaultMutableTreeNode parent;

          MavenProject aggregator = projectsManager.findAggregator(mavenProject);
          if (aggregator != null) {
            parent = projectsToNode.get(aggregator);
          }
          else {
            parent = root;
          }

          parent.add(projectsToNode.get(mavenProject));
        }

        return root;
      }
    });
  }
}
