package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.SimpleTreeBuilder;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.events.MavenEventsManager;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.ProjectBundle;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.utils.IdeaAPIHelper;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.InputEvent;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@State(name = "MavenProjectNavigator", storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")})
public class MavenProjectsNavigator extends MavenProjectsStructure implements ProjectComponent, PersistentStateComponent<MavenProjectsNavigatorSettings> {
  public static final String TOOL_WINDOW_ID = "Maven Projects";
  private static final Icon ICON = IconLoader.getIcon("/images/mavenEmblem.png");

  private static final URL ADD_ICON_URL = MavenProjectsNavigator.class.getResource("/general/add.png");
  private static final URL SYNC_ICON_URL = MavenProjectsNavigator.class.getResource("/actions/sync.png");

  private MavenProjectsNavigatorSettings mySettings = new MavenProjectsNavigatorSettings();

  private SimpleTreeBuilder myTreeBuilder;
  private SimpleTree myTree;

  private final Map<VirtualFile, PomNode> myFileToNode = new LinkedHashMap<VirtualFile, PomNode>();

  public static MavenProjectsNavigator getInstance(Project project) {
    return project.getComponent(MavenProjectsNavigator.class);
  }

  public MavenProjectsNavigator(Project project,
                               MavenProjectsManager projectsManager,
                               MavenEventsManager eventsHandler) {
    super(project, projectsManager, eventsHandler);
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

  public void projectOpened() {
    if (myProject.isDefault()) return;
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) return;

    initMavenProjectsTree();
    initToolWindow();
    listenForChanges();
  }

  public void projectClosed() {
  }

  private void initMavenProjectsTree() {
    myTree = new SimpleTree() {
      private final JLabel myLabel = new JLabel(ProjectBundle.message("maven.navigator.nothing.to.display",
                                                                formatHtmlImage(ADD_ICON_URL),
                                                                formatHtmlImage(SYNC_ICON_URL)));

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (myProjectsManager.isMavenizedProject()) return;

        myLabel.setFont(getFont());
        myLabel.setBackground(getBackground());
        myLabel.setForeground(getForeground());
        Rectangle bounds = getBounds();
        Dimension size = myLabel.getPreferredSize();
        myLabel.setBounds(0, 0, size.width, size.height);

        int x = (bounds.width - size.width) / 2;
        Graphics g2 = g.create(bounds.x + x, bounds.y + 20, bounds.width, bounds.height);
        try {
          myLabel.paint(g2);
        }
        finally {
          g2.dispose();
        }
      }
    };

    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(true);
    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);

    myTreeBuilder = new SimpleTreeBuilder(myTree, (DefaultTreeModel)myTree.getModel(), this, null);
    myTreeBuilder.initRoot();

    Disposer.register(myProject, myTreeBuilder);

    IdeaAPIHelper.installCheckboxRenderer(myTree, new IdeaAPIHelper.CheckboxHandler() {
      public void toggle(final TreePath treePath, final InputEvent e) {
        final SimpleNode node = myTree.getNodeFor(treePath);
        if (node != null) {
          node.handleDoubleClickOrEnter(myTree, e);
        }
      }

      public boolean isVisible(final Object userObject) {
        return userObject instanceof ProfileNode;
      }

      public boolean isSelected(final Object userObject) {
        return ((ProfileNode)userObject).isActive();
      }
    });
  }

  private void listenForChanges() {
    MyProjectsListener projectsListener = new MyProjectsListener();
    myProjectsManager.addListener(projectsListener);
    myProjectsManager.addProjectsTreeListener(projectsListener);

    myEventsHandler.addListener(new MavenEventsManager.Listener() {
      public void updateShortcuts(@Nullable String actionId) {
        for (PomNode pomNode : myFileToNode.values()) {
          pomNode.updateShortcuts(actionId);
        }
      }
    });

    myEventsHandler.installTaskSelector(new MavenEventsManager.TaskSelector() {
      SelectMavenGoalDialog dialog;

      public boolean select(final Project project,
                            @Nullable final String pomPath,
                            @Nullable final String goal,
                            @NotNull final String title) {
        dialog = new SelectMavenGoalDialog(project, pomPath, goal, title);
        dialog.show();
        return dialog.isOK();
      }

      public String getSelectedPomPath() {
        return dialog.getSelectedPomPath();
      }

      public String getSelectedGoal() {
        return dialog.getSelectedGoal();
      }
    });
  }

  private void initToolWindow() {
    final JPanel navigatorPanel = new MavenProjectsNavigatorPanel(myProject, myTree);

    ToolWindow pomToolWindow = ToolWindowManager.getInstance(myProject)
        .registerToolWindow(TOOL_WINDOW_ID, navigatorPanel, ToolWindowAnchor.RIGHT, myProject);
    pomToolWindow.setIcon(ICON);
  }

  public MavenProjectsNavigatorSettings getTreeViewSettings() {
    return mySettings;
  }

  public MavenProjectsNavigatorSettings getState() {
    return mySettings;
  }

  public void loadState(MavenProjectsNavigatorSettings state) {
    mySettings = state;
  }

  protected void updateTreeFrom(@Nullable SimpleNode node) {
    if (node != null) {
      final DefaultMutableTreeNode mutableTreeNode = TreeUtil.findNodeWithObject((DefaultMutableTreeNode)myTree.getModel().getRoot(), node);
      if (mutableTreeNode != null) {
        myTreeBuilder.addSubtreeToUpdate(mutableTreeNode);
        return;
      }
    }
    updateFromRoot(node == myRoot, false);
  }

  public void updateFromRoot(boolean rebuild, boolean restructure) {
    if (restructure) {
      myRoot.rebuild(myFileToNode.values());
    }
    myTreeBuilder.updateFromRoot(rebuild);
    if (rebuild) {
      myTree.expandPath(new TreePath(myTree.getModel().getRoot()));
    }
  }

  public void selectInTree(VirtualFile file) {
    PomNode node = myFileToNode.get(file);
    if (node != null) selectNode(myTreeBuilder, node);
  }

  private class MyProjectsListener implements MavenProjectsManager.Listener, MavenProjectsTree.Listener {
    boolean isActivated;

    public void activated() {
      for (MavenProject each : myProjectsManager.getProjects()) {
        myFileToNode.put(each.getFile(), new PomNode(each));
      }

      updateFromRoot(true, true);
      isActivated = true;
    }

    public void setIgnored(MavenProject project, boolean on) {
      if (!isActivated) return;

      final PomNode pomNode = myFileToNode.get(project);
      if (pomNode != null) {
        pomNode.setIgnored(on);
      }
    }

    public void profilesChanged(List<String> profiles) {
      if (!isActivated) return;

      myRoot.setActiveProfiles(profiles);
      myTree.repaint();
    }

    public void projectsRead(final List<MavenProject> projects) {
      scheduleUpdateTask(new Runnable() {
        public void run() {
          doOnProjectChange(projects);
        }
      });
    }

    public void projectRead(MavenProject project) {
    }

    public void projectAggregatorChanged(MavenProject project) {
      onProjectChange(project);
    }

    public void projectResolved(boolean quickResolve, MavenProject project, org.apache.maven.project.MavenProject nativeMavenProject) {
      onProjectChange(project);
    }

    private void onProjectChange(final MavenProject project) {
      scheduleUpdateTask(new Runnable() {
        public void run() {
          doOnProjectChange(Collections.singletonList(project));
        }
      });
    }

    private void doOnProjectChange(List<MavenProject> projects) {
      boolean shouldUpdate = false;
      for (MavenProject each : projects) {
        final PomNode pomNode = myFileToNode.get(each.getFile());
        if (pomNode != null) {
          pomNode.onFileUpdate();
        }
        else {
          final PomNode newNode = new PomNode(each);
          myFileToNode.put(each.getFile(), newNode);
          myRoot.addToStructure(newNode);
          shouldUpdate = true;
        }
      }
      if (shouldUpdate) updateFromRoot(true, true);
      myRoot.updateProfileNodes();
      myTree.repaint();
    }

    public void projectRemoved(final MavenProject project) {
      scheduleUpdateTask(new Runnable() {
        public void run() {
          final PomNode pomNode = myFileToNode.get(project.getFile());
          if (pomNode != null) {
            myFileToNode.remove(project.getFile());
            pomNode.removeFromParent();
          }
          myRoot.updateProfileNodes();
          myTree.repaint();
        }
      });
    }

    private void scheduleUpdateTask(final Runnable r) {
      if (!isActivated) return;
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;
          r.run();
        }
      });
    }
  }
}
