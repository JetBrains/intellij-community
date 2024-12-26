// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.ui.tree.AsyncTreeModel;
import com.intellij.ui.tree.StructureTreeModel;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenProfileKind;
import org.jetbrains.idea.maven.navigator.MavenProjectsNavigator;
import org.jetbrains.idea.maven.project.MavenPluginInfo;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenIndexUpdateState;
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.utils.MavenArtifactUtil;
import org.jetbrains.idea.maven.utils.MavenUIUtil;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.swing.tree.TreePath;
import java.awt.event.InputEvent;
import java.util.*;
import java.util.concurrent.ExecutorService;

public class MavenProjectsStructure extends SimpleTreeStructure {
  public enum MavenStructureDisplayMode {
    SHOW_ALL, SHOW_PROJECTS, SHOW_GOALS
  }

  private final ExecutorService boundedUpdateService;

  private final Project myProject;
  private final MavenStructureDisplayMode myDisplayMode;
  private final MavenProjectsManager myProjectsManager;
  private final MavenTasksManager myTasksManager;
  private final MavenShortcutsManager myShortcutsManager;
  private final MavenProjectsNavigator myProjectsNavigator;

  private final RootNode myRoot;
  private final StructureTreeModel<MavenProjectsStructure> myModel;
  private final SimpleTree myTree;
  private volatile boolean isUnloading = false;

  private final Map<MavenProject, ProjectNode> myProjectToNodeMapping = new HashMap<>();

  public MavenProjectsStructure(Project project,
                                MavenStructureDisplayMode displayMode,
                                MavenProjectsManager projectsManager,
                                MavenTasksManager tasksManager,
                                MavenShortcutsManager shortcutsManager,
                                MavenProjectsNavigator projectsNavigator,
                                SimpleTree tree) {
    myProject = project;
    myDisplayMode = displayMode;
    myRoot = new RootNode(this);
    myProjectsManager = projectsManager;
    myTasksManager = tasksManager;
    myShortcutsManager = shortcutsManager;
    myProjectsNavigator = projectsNavigator;
    boundedUpdateService = AppExecutorUtil.createBoundedApplicationPoolExecutor("Maven Plugin Updater", 1);
    project.getMessageBus().simpleConnect().subscribe(DynamicPluginListener.TOPIC, new DynamicPluginListener() {
      @Override
      public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        if (MavenUtil.INTELLIJ_PLUGIN_ID.equals(pluginDescriptor.getPluginId().getIdString())) {
          isUnloading = true;
        }
      }
    });

    myTree = tree;
    configureTree(myTree);
    myModel = new StructureTreeModel<>(this, projectsNavigator);
    tree.setModel(new AsyncTreeModel(myModel, projectsNavigator));
  }

  Project getProject() {
    return myProject;
  }

  MavenProjectsManager getProjectsManager() {
    return myProjectsManager;
  }

  MavenProjectsNavigator getProjectsNavigator() {
    return myProjectsNavigator;
  }

  public MavenTasksManager getTasksManager() {
    return myTasksManager;
  }

  public MavenShortcutsManager getShortcutsManager() {
    return myShortcutsManager;
  }

  MavenStructureDisplayMode getDisplayMode() {
    return myDisplayMode;
  }

  private static void configureTree(final SimpleTree tree) {
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);

    MavenUIUtil.installCheckboxRenderer(tree, new MavenUIUtil.CheckboxHandler() {
      @Override
      public void toggle(TreePath treePath, InputEvent e) {
        SimpleNode node = tree.getNodeFor(treePath);
        if (node != null) {
          node.handleDoubleClickOrEnter(tree, e);
        }
      }

      @Override
      public boolean isVisible(Object userObject) {
        return userObject instanceof ProfileNode;
      }

      @Override
      public MavenUIUtil.CheckBoxState getState(Object userObject) {
        MavenProfileKind state = ((ProfileNode)userObject).getState();
        return switch (state) {
          case NONE -> MavenUIUtil.CheckBoxState.UNCHECKED;
          case EXPLICIT -> MavenUIUtil.CheckBoxState.CHECKED;
          case IMPLICIT -> MavenUIUtil.CheckBoxState.PARTIAL;
        };
      }
    });
  }

  @Override
  public @NotNull RootNode getRootElement() {
    return myRoot;
  }

  public void update() {
    List<MavenProject> projects = getProjectsManager().getProjects();
    Set<MavenProject> deleted = new HashSet<>(myProjectToNodeMapping.keySet());
    deleted.removeAll(projects);
    updateProjects(projects, deleted);
  }

  void updateFrom(SimpleNode node) {
    if (node != null) {
      myModel.invalidate(node, true);
    }
  }

  void updateUpTo(SimpleNode node) {
    SimpleNode each = node;
    while (each != null) {
      updateFrom(each);
      each = each.getParent();
    }
  }

  public void updateProjects(List<MavenProject> updated, Collection<MavenProject> deleted) {
    for (MavenProject each : updated) {
      ProjectNode node = findNodeFor(each);
      if (node == null) {
        node = new ProjectNode(this, each);
        myProjectToNodeMapping.put(each, node);
      }
      doUpdateProject(node);
    }

    for (MavenProject each : deleted) {
      ProjectNode node = myProjectToNodeMapping.remove(each);
      if (node != null) {
        ProjectsGroupNode parent = node.getGroup();
        parent.remove(node);
      }
    }

    myRoot.updateProfiles();
  }

  private void doUpdateProject(ProjectNode node) {
    MavenProject project = node.getMavenProject();

    ProjectsGroupNode newParentNode = myRoot;

    if (getProjectsNavigator().getGroupModules()) {
      MavenProject aggregator = getProjectsManager().findAggregator(project);
      if (aggregator != null) {
        ProjectNode aggregatorNode = findNodeFor(aggregator);
        if (aggregatorNode != null && aggregatorNode.isVisible()) {
          newParentNode = aggregatorNode;
        }
      }
    }

    node.updateProject();
    reconnectNode(node, newParentNode);

    ProjectsGroupNode newModulesParentNode = getProjectsNavigator().getGroupModules() && node.isVisible() ? node : myRoot;
    for (MavenProject each : getProjectsManager().getModules(project)) {
      ProjectNode moduleNode = findNodeFor(each);
      if (moduleNode != null && !moduleNode.getParent().equals(newModulesParentNode)) {
        reconnectNode(moduleNode, newModulesParentNode);
      }
    }
  }

  private static void reconnectNode(ProjectNode node, ProjectsGroupNode newParentNode) {
    ProjectsGroupNode oldParentNode = node.getGroup();
    if (oldParentNode == null || !oldParentNode.equals(newParentNode)) {
      if (oldParentNode != null) {
        oldParentNode.remove(node);
      }
      newParentNode.add(node);
    }
    else {
      newParentNode.sortProjects();
    }
  }

  public void updateProfiles() {
    myRoot.updateProfiles();
  }

  public void updateIgnored(List<MavenProject> projects) {
    for (MavenProject each : projects) {
      ProjectNode node = findNodeFor(each);
      if (node == null) continue;
      node.updateIgnored();
    }
  }

  public void accept(@NotNull TreeVisitor visitor) {
    ((TreeVisitor.Acceptor)myTree.getModel()).accept(visitor);
  }

  public void updateGoals() {
    for (ProjectNode each : myProjectToNodeMapping.values()) {
      each.updateGoals();
    }
  }

  public void updateRunConfigurations() {
    for (ProjectNode each : myProjectToNodeMapping.values()) {
      each.updateRunConfigurations();
    }
  }

  public void select(MavenProject project) {
    ProjectNode node = findNodeFor(project);
    if (node != null) select(node);
  }

  public void select(SimpleNode node) {
    myModel.select(node, myTree, treePath -> {
    });
  }

  private ProjectNode findNodeFor(MavenProject project) {
    return myProjectToNodeMapping.get(project);
  }

  enum DisplayKind {
    ALWAYS, NEVER, NORMAL
  }

  protected boolean showOnlyBasicPhases() {
    if (getDisplayMode() == MavenStructureDisplayMode.SHOW_GOALS) {
      return false;
    }
    return getProjectsNavigator().getShowBasicPhasesOnly();
  }

  public static <T extends MavenSimpleNode> List<T> getSelectedNodes(SimpleTree tree, Class<T> nodeClass) {
    final List<T> filtered = new ArrayList<>();
    for (SimpleNode node : getSelectedNodes(tree)) {
      if ((nodeClass != null) && (!nodeClass.isInstance(node))) {
        filtered.clear();
        break;
      }
      //noinspection unchecked
      filtered.add((T)node);
    }
    return filtered;
  }

  private static List<SimpleNode> getSelectedNodes(SimpleTree tree) {
    List<SimpleNode> nodes = new ArrayList<>();
    TreePath[] treePaths = tree.getSelectionPaths();
    if (treePaths != null) {
      for (TreePath treePath : treePaths) {
        nodes.add(tree.getNodeFor(treePath));
      }
    }
    return nodes;
  }

  public enum ErrorLevel {
    NONE, ERROR
  }

  void updatePluginsTree(PluginsNode pluginsNode, List<MavenPluginInfo> pluginInfos) {
    boundedUpdateService.execute(new MavenProjectsStructure.UpdatePluginsTreeTask(pluginsNode, pluginInfos));
  }

  private class UpdatePluginsTreeTask implements Runnable {
    private final @NotNull PluginsNode myParentNode;
    private final List<MavenPluginInfo> myPluginInfos;

    UpdatePluginsTreeTask(@NotNull PluginsNode parentNode, List<MavenPluginInfo> pluginInfos) {
      myParentNode = parentNode;
      myPluginInfos = pluginInfos;
    }


    @Override
    public void run() {
      List<PluginNode> pluginInfos = new ArrayList<>();
      var iterator = myPluginInfos.iterator();
      while (!isUnloading && iterator.hasNext()) {
        var next = iterator.next();
        var pluginInfo = MavenArtifactUtil.readPluginInfo(next.getArtifact());
        var pluginNode = new PluginNode(MavenProjectsStructure.this, myParentNode, next.getPlugin(), pluginInfo);
        pluginInfos.add(pluginNode);
      }
      updateNodesInEDT(pluginInfos);
    }

    private void updateNodesInEDT(List<PluginNode> pluginNodes) {
      ApplicationManager.getApplication().invokeLater(() -> {
        myParentNode.getPluginNodes().clear();
        if (isUnloading) return;
        myParentNode.getPluginNodes().addAll(pluginNodes);
        myParentNode.sort(myParentNode.getPluginNodes());
        myParentNode.childrenChanged();
      });
    }
  }

  public void updateRepositoryStatus(@NotNull MavenIndexUpdateState state) {
    myProjectToNodeMapping.values().forEach(pn -> {
      pn.getRepositoriesNode().updateStatus(state);
    });
  }
}