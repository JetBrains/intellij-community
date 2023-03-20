// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.navigator.structure;

import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.mac.touchbar.Touchbar;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenGoalLocation;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.model.MavenConstants;
import org.jetbrains.idea.maven.model.MavenProfileKind;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.*;

import static org.jetbrains.idea.maven.navigator.MavenProjectsNavigator.TOOL_WINDOW_PLACE_ID;

public final class MavenProjectsNavigatorPanel extends SimpleToolWindowPanel implements DataProvider {
  private final Project myProject;
  private final SimpleTree myTree;

  private final Comparator<String> myGoalOrderComparator = new Comparator<>() {

    private Map<String, Integer> standardGoalOrder;

    @Override
    public int compare(String o1, String o2) {
      return getStandardGoalOrder(o1) - getStandardGoalOrder(o2);
    }

    private int getStandardGoalOrder(String goal) {
      if (standardGoalOrder == null) {
        standardGoalOrder = new HashMap<>();
        int i = 0;
        for (String aGoal : MavenConstants.PHASES) {
          standardGoalOrder.put(aGoal, i++);
        }
      }
      Integer order = standardGoalOrder.get(goal);
      return order != null ? order.intValue() : standardGoalOrder.size();
    }
  };

  public MavenProjectsNavigatorPanel(Project project, SimpleTree tree) {
    super(true, true);
    myProject = project;
    myTree = tree;

    final ActionManager actionManager = ActionManager.getInstance();
    ActionToolbar actionToolbar = actionManager.createActionToolbar("Maven Navigator Toolbar",
                                                                    (DefaultActionGroup)actionManager
                                                                      .getAction("Maven.NavigatorActionsToolbar"),
                                                                    true);

    actionToolbar.setTargetComponent(tree);
    setToolbar(actionToolbar.getComponent());
    setContent(ScrollPaneFactory.createScrollPane(myTree));

    setTransferHandler(new MyTransferHandler(project));

    myTree.addMouseListener(new PopupHandler() {
      @Override
      public void invokePopup(final Component comp, final int x, final int y) {
        final String id = getMenuId(MavenProjectsStructure.getSelectedNodes(myTree, MavenSimpleNode.class));
        if (id != null) {
          final ActionGroup actionGroup = (ActionGroup)actionManager.getAction(id);
          if (actionGroup != null) {
            actionManager.createActionPopupMenu(TOOL_WINDOW_PLACE_ID, actionGroup).getComponent().show(comp, x, y);
          }
        }
      }

      @Nullable
      private static String getMenuId(Collection<? extends MavenSimpleNode> nodes) {
        String id = null;
        for (MavenSimpleNode node : nodes) {
          String menuId = node.getMenuId();
          if (menuId == null) {
            return null;
          }
          if (id == null) {
            id = menuId;
          }
          else if (!id.equals(menuId)) {
            return null;
          }
        }
        return id;
      }
    });

    Touchbar.setActions(this, "Maven.Reimport");
  }

  @Override
  @Nullable
  public Object getData(@NotNull @NonNls String dataId) {
    if (PlatformCoreDataKeys.BGT_DATA_PROVIDER.is(dataId)) {
      @NotNull List<MavenSimpleNode> selectedNodes = MavenProjectsStructure.getSelectedNodes(myTree, MavenSimpleNode.class);
      return (DataProvider)slowId -> getSlowData(slowId, selectedNodes);
    }
    if (PlatformCoreDataKeys.HELP_ID.is(dataId)) return "reference.toolWindows.mavenProjects";
    if (CommonDataKeys.PROJECT.is(dataId)) return myProject;

    if (MavenDataKeys.MAVEN_PROJECTS_TREE.is(dataId)) {
      return myTree;
    }
    return super.getData(dataId);
  }

  private @Nullable Object getSlowData(@NotNull String dataId,
                                       @NotNull List<MavenSimpleNode> selectedNodes) {
    if (CommonDataKeys.VIRTUAL_FILE.is(dataId)) return extractVirtualFile(selectedNodes);
    if (CommonDataKeys.VIRTUAL_FILE_ARRAY.is(dataId)) return extractVirtualFiles(selectedNodes);

    if (Location.DATA_KEY.is(dataId)) return extractLocation(selectedNodes);
    if (CommonDataKeys.NAVIGATABLE_ARRAY.is(dataId)) return extractNavigatables(selectedNodes);

    if (MavenDataKeys.MAVEN_GOALS.is(dataId)) return extractGoals(true, selectedNodes);
    if (MavenDataKeys.RUN_CONFIGURATION.is(dataId)) return extractRunSettings(selectedNodes);
    if (MavenDataKeys.MAVEN_PROFILES.is(dataId)) return extractProfiles(selectedNodes);

    if (MavenDataKeys.MAVEN_DEPENDENCIES.is(dataId)) {
      return extractDependencies(selectedNodes);
    }

    return null;
  }

  private static VirtualFile extractVirtualFile(@NotNull List<MavenSimpleNode> selectedNodes) {
    for (MavenSimpleNode each : selectedNodes) {
      VirtualFile file = each.getVirtualFile();
      if (file != null && file.isValid()) return file;
    }

    var projectNode = getContextProjectNode(selectedNodes);
    if (projectNode == null) return null;
    VirtualFile file = projectNode.getMavenProject().getFile();
    if (!file.isValid()) return null;
    return file;
  }

  private static Object extractVirtualFiles(@NotNull List<MavenSimpleNode> selectedNodes) {
    final List<VirtualFile> files = new ArrayList<>();
    for (MavenSimpleNode each : selectedNodes) {
      VirtualFile file = each.getVirtualFile();
      if (file != null && file.isValid()) files.add(file);
    }
    return files.isEmpty() ? null : VfsUtilCore.toVirtualFileArray(files);
  }

  private static Object extractNavigatables(@NotNull List<MavenSimpleNode> selectedNodes) {
    final List<Navigatable> navigatables = new ArrayList<>();
    for (MavenSimpleNode each : selectedNodes) {
      Navigatable navigatable = each.getNavigatable();
      if (navigatable != null) navigatables.add(navigatable);
    }
    return navigatables.isEmpty() ? null : navigatables.toArray(Navigatable.EMPTY_NAVIGATABLE_ARRAY);
  }

  private Object extractLocation(@NotNull List<MavenSimpleNode> selectedNodes) {
    VirtualFile file = extractVirtualFile(selectedNodes);
    if (file == null) return null;

    List<String> goals = extractGoals(false, selectedNodes);
    if (goals == null) return null;

    PsiFile psiFile = PsiManager.getInstance(myProject).findFile(file);
    return psiFile == null ? null : new MavenGoalLocation(myProject, psiFile, goals);
  }

  @Nullable
  private static RunnerAndConfigurationSettings extractRunSettings(@NotNull List<MavenSimpleNode> selectedNodes) {
    @Nullable MavenSimpleNode node = selectedNodes.isEmpty() ? null : selectedNodes.get(0);
    if (!(node instanceof RunConfigurationNode)) return null;

    return ((RunConfigurationNode)node).getSettings();
  }

  private List<String> extractGoals(boolean qualifiedGoals,
                                    @NotNull List<MavenSimpleNode> selectedNodes) {

    List<ProjectNode> projectNodes = filterNodesByClass(selectedNodes, ProjectNode.class);
    final ProjectNode projectNode = projectNodes.size() == 1 ? projectNodes.get(0) : null;
    if (projectNode != null) {
      MavenProject project = projectNode.getMavenProject();
      String goal = project.getDefaultGoal();
      if (!StringUtil.isEmptyOrSpaces(goal)) {
        // Maven uses StringTokenizer to split defaultGoal. See DefaultLifecycleTaskSegmentCalculator#calculateTaskSegments()
        return ContainerUtil.newArrayList(StringUtil.tokenize(new StringTokenizer(goal)));
      }
    }
    else {
      List<MavenGoalNode> goalNodes = filterNodesByClass(selectedNodes, MavenGoalNode.class);
      if (getCommonProjectNode(goalNodes) == null) {
        return null;
      }

      final List<String> goals = new ArrayList<>();
      for (MavenGoalNode node : goalNodes) {
        goals.add(qualifiedGoals ? node.getGoal() : node.getName());
      }
      goals.sort(myGoalOrderComparator);
      return goals;
    }
    return null;
  }

  @Nullable
  private static MavenProjectNode getCommonProjectNode(Collection<? extends MavenSimpleNode> nodes) {
    MavenProjectNode parent = null;
    for (MavenSimpleNode node : nodes) {
      var nextParent = node.findParentProjectNode();
      if (parent == null) {
        parent = nextParent;
      }
      else if (parent != nextParent) {
        return null;
      }
    }
    return parent;
  }

  private static Object extractProfiles(@NotNull List<MavenSimpleNode> selectedNodes) {
    List<ProfileNode> profileNodes = filterNodesByClass(selectedNodes, ProfileNode.class);
    final Map<String, MavenProfileKind> profiles = new HashMap<>();
    for (ProfileNode node : profileNodes) {
      profiles.put(node.getProfileName(), node.getState());
    }
    return profiles;
  }

  private static Set<MavenArtifact> extractDependencies(@NotNull List<MavenSimpleNode> selectedNodes) {
    Set<MavenArtifact> result = new HashSet<>();
    List<ProjectNode> projectNodes = filterNodesByClass(selectedNodes, ProjectNode.class);


    if (!projectNodes.isEmpty()) {
      for (ProjectNode each : projectNodes) {
        result.addAll(each.getMavenProject().getDependencies());
      }
      return result;
    }


    List<BaseDependenciesNode> baseDependenciesNodes =
      filterNodesByClass(selectedNodes, BaseDependenciesNode.class);

    for (BaseDependenciesNode each : baseDependenciesNodes) {
      if (each instanceof DependenciesNode) {
        result.addAll(each.getMavenProject().getDependencies());
      }
      else {
        result.add(((DependencyNode)each).getArtifact());
      }
    }
    return result;
  }

  private static <T extends MavenSimpleNode> List<T> filterNodesByClass(@NotNull List<MavenSimpleNode> nodes,
                                                                        Class<T> aClass) {
    List<T> filtered = ContainerUtil.filterIsInstance(nodes, aClass);
    return filtered.size() == nodes.size() ? filtered : Collections.emptyList();
  }


  private static @Nullable MavenProjectNode getContextProjectNode(@NotNull List<MavenSimpleNode> selectedNodes) {
    List<ProjectNode> projectNodes = filterNodesByClass(selectedNodes, ProjectNode.class);
    ProjectNode projectNode = projectNodes.size() == 1 ? projectNodes.get(0) : null;
    if (projectNode != null) return projectNode;
    return getCommonProjectNode(selectedNodes);
  }

  private static final class MyTransferHandler extends TransferHandler {

    private final Project myProject;

    private MyTransferHandler(Project project) {
      myProject = project;
    }

    @Override
    public boolean importData(final TransferSupport support) {

      //todo new maven importing
      if (canImport(support)) {
        List<VirtualFile> pomFiles = new ArrayList<>();

        final List<File> fileList = FileCopyPasteUtil.getFileList(support.getTransferable());
        if (fileList == null) return false;

        MavenProjectsManager manager = MavenProjectsManager.getInstance(myProject);

        for (File file : fileList) {
          VirtualFile virtualFile = VfsUtil.findFileByIoFile(file, true);
          if (file.isFile()
              && virtualFile != null
              && MavenActionUtil.isMavenProjectFile(virtualFile)
              && !manager.isManagedFile(virtualFile)) {
            pomFiles.add(virtualFile);
          }
        }

        if (pomFiles.isEmpty()) {
          return false;
        }

        manager.addManagedFilesOrUnignore(pomFiles);

        return true;
      }
      return false;
    }

    @Override
    public boolean canImport(final TransferSupport support) {
      return FileCopyPasteUtil.isFileListFlavorAvailable(support.getDataFlavors());
    }
  }
}