package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.DataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.MavenDataKeys;
import org.jetbrains.idea.maven.core.util.MavenEnv;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.state.MavenProjectsState;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
class MavenNavigatorPanel extends JPanel implements DataProvider {

  private final Project myProject;
  private final MavenProjectsState myProjectsState;
  private final SimpleTree myTree;

  private Map<String, Integer> standardGoalOrder;

  private Comparator<String> myGoalOrderComparator = new Comparator<String>() {
    public int compare(String o1, String o2) {
      return getStandardGoalOrder(o1) - getStandardGoalOrder(o2);
    }
  };

  public MavenNavigatorPanel(Project project, MavenProjectsState projectsState, SimpleTree tree) {
    myProject = project;
    myProjectsState = projectsState;
    myTree = tree;

    setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));

    add(ActionManager.getInstance().createActionToolbar("New Maven Toolbar", (ActionGroup)ActionManager.getInstance()
      .getAction("Maven.PomTreeToolbar"), true).getComponent(),
        new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_NORTH, GridConstraints.FILL_HORIZONTAL, GridConstraints
          .SIZEPOLICY_CAN_SHRINK | GridConstraints
          .SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(-1, 20), null));

    add(new JScrollPane(myTree), new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_SOUTH, GridConstraints.FILL_BOTH,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                               GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW,
                                                               null, null, null));

    myTree.addMouseListener(new PopupHandler() {
      public void invokePopup(final Component comp, final int x, final int y) {
        final String id = getMenuId(getSelectedNodes(PomTreeStructure.CustomNode.class));
        if (id != null) {
          final ActionGroup actionGroup = (ActionGroup)ActionManager.getInstance().getAction(id);
          if (actionGroup != null) {
            ActionManager.getInstance().createActionPopupMenu("", actionGroup).getComponent().show(comp, x, y);
          }
        }
      }

      @Nullable
      private String getMenuId(Collection<? extends PomTreeStructure.CustomNode> nodes) {
        String id = null;
        for (PomTreeStructure.CustomNode node : nodes) {
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
    if (dataId.equals(DataKeys.PROJECT.getName())) {
      return myProject;
    }
    if (dataId.equals(DataKeys.NAVIGATABLE_ARRAY.getName())) {
      final List<Navigatable> navigatables = new ArrayList<Navigatable>();
      for (PomTreeStructure.PomNode pomNode : getSelectedPomNodes()) {
        final Navigatable navigatable = pomNode.getNavigatable();
        if(navigatable!=null){
          navigatables.add(navigatable);
        }
      }
      return navigatables.isEmpty() ? null : navigatables.toArray(new Navigatable[navigatables.size()]);
    }
    if (dataId.equals(DataKeys.VIRTUAL_FILE.getName())) {
      final PomTreeStructure.PomNode pomNode = getContextPomNode();
      return pomNode != null ? pomNode.getFile() : null;
    }
    if (dataId.equals(DataKeys.VIRTUAL_FILE_ARRAY.getName())) {
      final List<VirtualFile> files = new ArrayList<VirtualFile>();
      for (PomTreeStructure.PomNode pomNode : getSelectedPomNodes()) {
        files.add(pomNode.getFile());
      }
      return files.isEmpty() ? null : files.toArray(new VirtualFile[files.size()]);
    }
    if (dataId.equals(MavenDataKeys.MAVEN_GOALS_KEY.getName())) {
      final PomTreeStructure.PomNode pomNode = getSelectedPomNode();
      if (pomNode != null) {
        final MavenProject mavenProject = myProjectsState.getMavenProject(pomNode.getFile());
        if (mavenProject != null) {
          final String goal = mavenProject.getBuild().getDefaultGoal();
          if (!StringUtil.isEmptyOrSpaces(goal)) {
            return Collections.singletonList(goal);
          }
        }
      }
      else {
        final List<PomTreeStructure.GoalNode> nodes = getSelectedNodes(PomTreeStructure.GoalNode.class);
        if(PomTreeStructure.getCommonParent(nodes)==null) {
          return null;
        }
        final List<String> goals = new ArrayList<String>();
        for (PomTreeStructure.GoalNode node : nodes) {
          goals.add(node.getGoal());
        }
        Collections.sort(goals, myGoalOrderComparator);
        return goals;
      }
    }
    if (dataId.equals(MavenDataKeys.MAVEN_PROFILES_KEY.getName())) {
      final List<PomTreeStructure.ProfileNode> nodes = getSelectedNodes(PomTreeStructure.ProfileNode.class);
      if(PomTreeStructure.getCommonParent(nodes)==null) {
        return null;
      }
      final List<String> profiles = new ArrayList<String>();
      for (PomTreeStructure.ProfileNode node : nodes) {
        profiles.add(node.getProfile());
      }
      return profiles;
    }
    if (dataId.equals(MavenDataKeys.MAVEN_IDS.getName())) {
      final List<PomTreeStructure.PluginNode> nodes = getSelectedNodes(PomTreeStructure.PluginNode.class);
      if(PomTreeStructure.getCommonParent(nodes)==null) {
        return null;
      }
      final List<MavenId> ids = new ArrayList<MavenId>();
      for (PomTreeStructure.PluginNode node : nodes) {
        if(node.isDetachable()){
          ids.add(node.getId());
        } else {
          return null;
        }
      }
      return ids;
    }
    return null;
  }

  private <T extends SimpleNode> List<T> getSelectedNodes(final Class<T> aClass) {
    return PomTreeStructure.getSelectedNodes(myTree, aClass);
  }

  private List<PomTreeStructure.PomNode> getSelectedPomNodes() {
    return getSelectedNodes(PomTreeStructure.PomNode.class);
  }

  @Nullable
  private PomTreeStructure.PomNode getSelectedPomNode() {
    final List<PomTreeStructure.PomNode> pomNodes = getSelectedPomNodes();
    return pomNodes.size() == 1 ? pomNodes.get(0) : null;
  }

  @Nullable
  private PomTreeStructure.PomNode getContextPomNode() {
    final PomTreeStructure.PomNode pomNode = getSelectedPomNode();
    if (pomNode != null) {
      return pomNode;
    }
    else {
      return PomTreeStructure.getCommonParent(getSelectedNodes(PomTreeStructure.CustomNode.class));
    }
  }

  private int getStandardGoalOrder(String goal) {
    if (standardGoalOrder == null) {
      standardGoalOrder = new HashMap<String, Integer>();
      int i = 0;
      for (String aGoal : MavenEnv.getStandardGoalsList()) {
        standardGoalOrder.put(aGoal, i++);
      }
    }
    Integer order = standardGoalOrder.get(goal);
    return order != null ? order.intValue() : standardGoalOrder.size();
  }
}
