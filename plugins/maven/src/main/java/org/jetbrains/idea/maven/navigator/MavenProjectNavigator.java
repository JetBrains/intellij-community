package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.SimpleTreeBuilder;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.repo.MavenRepository;
import org.jetbrains.idea.maven.state.MavenProjectsState;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.*;
import java.util.List;

@State(name = "MavenProjectNavigator", storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")})
public class MavenProjectNavigator extends PomTreeStructure implements ProjectComponent, MavenProjectsState.Listener, PersistentStateComponent<MavenNavigatorState> {

  public static MavenProjectNavigator getInstance(Project project) {
    return project.getComponent(MavenProjectNavigator.class);
  }

  @NonNls private static final String MAVEN_NAVIGATOR_TOOLWINDOW_ID = "Maven projects";

  private final Icon myIcon = IconLoader.getIcon("/images/mavenEmblem.png");

  private MavenNavigatorState settings = new MavenNavigatorState();

  private final SimpleTreeBuilder treeBuilder;
  final SimpleTree tree;

  private Map<VirtualFile, PomNode> fileToNode = new HashMap<VirtualFile, PomNode>();

  public MavenProjectNavigator(Project project, MavenProjectsState projectsState, MavenRepository repository) {
    super(project, projectsState, repository);

    tree = new SimpleTree();
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    tree.addMouseListener(new PomTreePopupHandler());

    treeBuilder = new SimpleTreeBuilder(tree, (DefaultTreeModel)tree.getModel(), this, null);
    treeBuilder.initRoot();
    Disposer.register(project, treeBuilder);

    StartupManager.getInstance(project).registerPostStartupActivity(new Runnable() {
      public void run() {
        createToolWindow();
      }
    });
  }

  public void createFile(final VirtualFile file) {
    final PomNode newNode = new PomNode(file);
    fileToNode.put(file, newNode);
    root.addToStructure(newNode);
  }

  public void updateFile(VirtualFile file) {
    final PomNode pomNode = fileToNode.get(file);
    if (pomNode != null) {
      pomNode.onFileUpdate();
    }
    else {
      createFile(file);
    }
  }

  public void removeFile(VirtualFile file) {
    final PomNode pomNode = fileToNode.get(file);
    if (pomNode != null) {
      fileToNode.remove(file);
      pomNode.removeFromParent();
    }
  }

  public void setIgnored(VirtualFile file, boolean on) {
    final PomNode pomNode = fileToNode.get(file);
    if(pomNode!=null){
      pomNode.setIgnored(on);
    }
  }

  public void setProfiles(VirtualFile file, @NotNull Collection<String> profiles) {
    final PomNode pomNode = fileToNode.get(file);
    if(pomNode!=null){
      pomNode.setProfiles(profiles);
    }
  }

  private void initTree() {
    final Map<VirtualFile, PomNode> oldFileToNode = fileToNode;
    fileToNode = new HashMap<VirtualFile, PomNode>();
    for (VirtualFile pomFile : myProjectsState.getFiles()) {
      PomNode pomNode = oldFileToNode.get(pomFile);
      if (pomNode == null) {
        pomNode = new PomNode(pomFile);
      }
      fileToNode.put(pomFile, pomNode);
    }

    updateFromRoot(true, true);

    myProjectsState.addListener(this);
  }

  private void createToolWindow() {
    final JPanel navigatorPanel = new MavenNavigatorPanel(this);

    ToolWindow pomToolWindow = ToolWindowManager.getInstance(project)
      .registerToolWindow(MAVEN_NAVIGATOR_TOOLWINDOW_ID, navigatorPanel, ToolWindowAnchor.RIGHT, project);
    pomToolWindow.setIcon(myIcon);

    new UiNotifyConnector(navigatorPanel, new Activatable() {
      public void showNotify() {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (project.isOpen()) {
              initTree();
            }
          }
        });
      }

      public void hideNotify() {
      }
    });
  }

  public PomTreeViewSettings getTreeViewSettings() {
    return settings;
  }

  public Map<String, Set<MavenId>> getExtraPluginState() {
    return settings.extraPlugins;
  }

  public MavenNavigatorState getState() {
    return settings;
  }

  public void loadState(MavenNavigatorState state) {
    settings = state;
  }

  protected Collection<PomNode> getAllPomNodes() {
    return fileToNode.values();
  }

  protected void updateTreeFrom(SimpleNode node) {
    final DefaultMutableTreeNode mutableTreeNode = TreeUtil.findNodeWithObject((DefaultMutableTreeNode)tree.getModel().getRoot(), node);
    if (mutableTreeNode != null) {
      treeBuilder.addSubtreeToUpdate(mutableTreeNode);
    }
    else {
      updateFromRoot(false, false);
    }
  }

  public void updateFromRoot(boolean rebuild, boolean restructure) {
    if (restructure) {
      final Collection<PomNode> allPomNodes = getAllPomNodes();
      for (PomNode node : allPomNodes) {
        node.unlinkNested();
      }
      root.rebuild(allPomNodes);
    }
    treeBuilder.updateFromRoot(rebuild);
    if (rebuild) {
      tree.expandPath(new TreePath(tree.getModel().getRoot()));
    }
  }

  public static PomNode getCommonParent(Collection<? extends CustomNode> goalNodes) {
    PomNode parent = null;
    for (CustomNode node : goalNodes) {
      PomNode nextParent = node.getParent(PomNode.class);
      if (parent == null) {
        parent = nextParent;
      }
      else if (parent != nextParent) {
        return null;
      }
    }
    return parent;
  }

  List<SimpleNode> getSelectedNodes() {
    List<SimpleNode> nodes = new ArrayList<SimpleNode>();
    TreePath[] treePaths = tree.getSelectionPaths();
    if (treePaths != null) {
      for (TreePath treePath : treePaths) {
        nodes.add(tree.getNodeFor(treePath));
      }
    }
    return nodes;
  }

  public <T extends SimpleNode> List<T> getSelectedNodes(Class<T> aClass, boolean strict) {
    return filterNodes(getSelectedNodes(), aClass, strict);
  }

  <T extends SimpleNode> List<T> filterNodes(Collection<SimpleNode> nodes, Class<T> aClass, boolean strict) {
    List<T> filtered = new ArrayList<T>();
    for (SimpleNode node : nodes) {
      if ((aClass != null) && (!aClass.isInstance(node) || (strict && aClass != node.getClass()))) {
        filtered.clear();
        break;
      }
      //noinspection unchecked
      filtered.add((T)node);
    }
    return filtered;
  }

  String getMenuId(Collection<? extends PomTreeStructure.CustomNode> nodes) {
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

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  @NonNls
  public String getComponentName() {
    return "MavenProjectNavigator";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public class PomTreePopupHandler extends PopupHandler {

    public void invokePopup(final Component comp, final int x, final int y) {
      final String id = getMenuId(getSelectedNodes(CustomNode.class, false));
      if (id != null) {
        final ActionGroup actionGroup = (ActionGroup)ActionManager.getInstance().getAction(id);
        if (actionGroup != null) {
          ActionManager.getInstance().createActionPopupMenu("", actionGroup).getComponent().show(comp, x, y);
        }
      }
    }
  }
}
