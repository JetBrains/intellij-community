package org.jetbrains.idea.maven.navigator;

import com.intellij.execution.Location;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.MavenDataKeys;
import org.jetbrains.idea.maven.embedder.MavenEmbedderFactory;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.runner.execution.MavenGoalLocation;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;


public class MavenNavigatorPanel extends JPanel implements DataProvider {

  private final Project myProject;
  private final MavenProjectsManager myProjectsManager;
  private final SimpleTree myTree;

  private Map<String, Integer> standardGoalOrder;

  private final Comparator<String> myGoalOrderComparator = new Comparator<String>() {
    public int compare(String o1, String o2) {
      return getStandardGoalOrder(o1) - getStandardGoalOrder(o2);
    }
  };

  public MavenNavigatorPanel(Project project, MavenProjectsManager projectsManager, SimpleTree tree) {
    myProject = project;
    myProjectsManager = projectsManager;
    myTree = tree;

    setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));

    add(ActionManager.getInstance().createActionToolbar("New Maven Toolbar", (ActionGroup)ActionManager.getInstance()
      .getAction("Maven.PomTreeToolbar"), true).getComponent(),
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL, GridConstraints
          .SIZEPOLICY_CAN_SHRINK | GridConstraints
          .SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 20), null));

    add(new JScrollPane(myTree), new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_SOUTH, GridConstraints.FILL_BOTH,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                     GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null,
                                                     null, null));

    myTree.addMouseListener(new PopupHandler() {
      public void invokePopup(final Component comp, final int x, final int y) {
        final String id = getMenuId(getSelectedNodes(MavenTreeStructure.CustomNode.class));
        if (id != null) {
          final ActionGroup actionGroup = (ActionGroup)ActionManager.getInstance().getAction(id);
          if (actionGroup != null) {
            ActionManager.getInstance().createActionPopupMenu("", actionGroup).getComponent().show(comp, x, y);
          }
        }
      }

      @Nullable
      private String getMenuId(Collection<? extends MavenTreeStructure.CustomNode> nodes) {
        String id = null;
        for (MavenTreeStructure.CustomNode node : nodes) {
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
  }

  @Nullable
  public Object getData(@NonNls String dataId) {
    if (dataId.equals(PlatformDataKeys.HELP_ID.getName())) return "reference.toolWindows.mavenProjects";

    if (dataId.equals(PlatformDataKeys.PROJECT.getName())) return myProject;

    if (dataId.equals(PlatformDataKeys.VIRTUAL_FILE.getName())) return extractVirtualFile();
    if (dataId.equals(PlatformDataKeys.VIRTUAL_FILE_ARRAY.getName())) return extractVirtualFiles();

    if (dataId.equals(Location.LOCATION)) return extractLocation();
    if (dataId.equals(PlatformDataKeys.NAVIGATABLE_ARRAY.getName())) return extractNavigatables();

    if (dataId.equals(MavenDataKeys.MAVEN_PROJECT_NODES.getName())) return extractPomNodes();
    if (dataId.equals(MavenDataKeys.MAVEN_GOALS_KEY.getName())) return extractGoals();
    if (dataId.equals(MavenDataKeys.MAVEN_PROFILES_KEY.getName())) return extractProfiles();

    return null;
  }

  private List<MavenProjectModel> extractPomNodes() {
    List<MavenProjectModel> result = new ArrayList<MavenProjectModel>();
    for (MavenTreeStructure.PomNode each : getSelectedPomNodes()) {
      result.add(each.getMavenProjectModel());
    }
    return result.isEmpty() ? null : result;
  }

  private VirtualFile extractVirtualFile() {
    final MavenTreeStructure.PomNode pomNode = getContextPomNode();
    if (pomNode == null) return null;
    VirtualFile file = pomNode.getFile();
    if (file == null || !file.isValid()) return null;
    return file;
  }

  private Object extractVirtualFiles() {
    final List<VirtualFile> files = new ArrayList<VirtualFile>();
    for (MavenTreeStructure.PomNode pomNode : getSelectedPomNodes()) {
      VirtualFile file = pomNode.getFile();
      if (file.isValid()) {
        files.add(file);
      }
    }
    return files.isEmpty() ? null : files.toArray(new VirtualFile[files.size()]);
  }

  private Object extractLocation() {
    VirtualFile file = extractVirtualFile();
    List<String> goals = extractGoals();
    if (file == null || goals == null) return null;

    return new MavenGoalLocation(myProject, file, extractGoals());
  }

  private Object extractNavigatables() {
    final List<Navigatable> navigatables = new ArrayList<Navigatable>();
    for (MavenTreeStructure.PomNode pomNode : getSelectedPomNodes()) {
      final Navigatable navigatable = pomNode.getNavigatable();
      if (navigatable != null) {
        navigatables.add(navigatable);
      }
    }
    return navigatables.isEmpty() ? null : navigatables.toArray(new Navigatable[navigatables.size()]);
  }

  private List<String> extractGoals() {
    final MavenTreeStructure.PomNode pomNode = getSelectedPomNode();
    if (pomNode != null) {
      MavenProjectModel project = pomNode.getMavenProjectModel();
      String goal = project.getDefaultGoal();
      if (!StringUtil.isEmptyOrSpaces(goal)) {
        return Collections.singletonList(goal);
      }
    }
    else {
      final List<MavenTreeStructure.GoalNode> nodes = getSelectedNodes(MavenTreeStructure.GoalNode.class);
      if (MavenTreeStructure.getCommonParent(nodes) == null) {
        return null;
      }
      final List<String> goals = new ArrayList<String>();
      for (MavenTreeStructure.GoalNode node : nodes) {
        goals.add(node.getGoal());
      }
      Collections.sort(goals, myGoalOrderComparator);
      return goals;
    }
    return null;
  }

  private Object extractProfiles() {
    final List<MavenTreeStructure.ProfileNode> nodes = getSelectedNodes(MavenTreeStructure.ProfileNode.class);
    if (MavenTreeStructure.getCommonParent(nodes) == null) {
      return null;
    }
    final List<String> profiles = new ArrayList<String>();
    for (MavenTreeStructure.ProfileNode node : nodes) {
      profiles.add(node.getProfile());
    }
    return profiles;
  }

  private <T extends SimpleNode> List<T> getSelectedNodes(final Class<T> aClass) {
    return MavenTreeStructure.getSelectedNodes(myTree, aClass);
  }

  private List<MavenTreeStructure.PomNode> getSelectedPomNodes() {
    return getSelectedNodes(MavenTreeStructure.PomNode.class);
  }

  @Nullable
  private MavenTreeStructure.PomNode getSelectedPomNode() {
    final List<MavenTreeStructure.PomNode> pomNodes = getSelectedPomNodes();
    return pomNodes.size() == 1 ? pomNodes.get(0) : null;
  }

  @Nullable
  private MavenTreeStructure.PomNode getContextPomNode() {
    final MavenTreeStructure.PomNode pomNode = getSelectedPomNode();
    if (pomNode != null) {
      return pomNode;
    }
    else {
      return MavenTreeStructure.getCommonParent(getSelectedNodes(MavenTreeStructure.CustomNode.class));
    }
  }

  private int getStandardGoalOrder(String goal) {
    if (standardGoalOrder == null) {
      standardGoalOrder = new HashMap<String, Integer>();
      int i = 0;
      for (String aGoal : MavenEmbedderFactory.getStandardGoalsList()) {
        standardGoalOrder.put(aGoal, i++);
      }
    }
    Integer order = standardGoalOrder.get(goal);
    return order != null ? order.intValue() : standardGoalOrder.size();
  }

}
