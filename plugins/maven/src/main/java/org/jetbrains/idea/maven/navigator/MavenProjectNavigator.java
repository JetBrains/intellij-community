package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
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
import org.jetbrains.idea.maven.core.util.IdeaAPIHelper;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.events.MavenEventsHandler;
import org.jetbrains.idea.maven.project.ProjectBundle;
import org.jetbrains.idea.maven.repo.MavenRepository;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.InputEvent;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

@State(name = "MavenProjectNavigator", storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")})
public class MavenProjectNavigator extends PomTreeStructure implements ProjectComponent, PersistentStateComponent<PomTreeViewSettings> {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.idea.maven.navigator.MavenProjectNavigator");

  public static MavenProjectNavigator getInstance(Project project) {
    return project.getComponent(MavenProjectNavigator.class);
  }

  @NonNls private static final String MAVEN_NAVIGATOR_TOOLWINDOW_ID = "Maven projects";

  private final Icon myIcon = IconLoader.getIcon("/images/mavenEmblem.png");

  private PomTreeViewSettings settings = new PomTreeViewSettings();

  private final SimpleTreeBuilder treeBuilder;
  final SimpleTree tree;

  private Map<VirtualFile, PomNode> fileToNode = new LinkedHashMap<VirtualFile, PomNode>();

  public MavenProjectNavigator(final Project project, MavenProjectsManager projectsManager, MavenRepository repository, MavenEventsHandler eventsHandler) {
    super(project, projectsManager, repository, eventsHandler);

    tree = new SimpleTree() {
      private JLabel myLabel = new JLabel(ProjectBundle.message("maven.please.reimport.xml"));

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        if (MavenProjectsManager.getInstance(project).isValidVersion()) return;

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
    tree.setRootVisible(false);
    tree.setShowsRootHandles(true);
    tree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    treeBuilder = new SimpleTreeBuilder(tree, (DefaultTreeModel)tree.getModel(), this, null);
    treeBuilder.initRoot();
    Disposer.register(project, treeBuilder);

    myProjectsManager.addListener(new MavenProjectsManager.Listener() {
      public void activate() {
        MavenProjectNavigator.LOG.assertTrue(fileToNode.isEmpty());

        for (VirtualFile file : myProjectsManager.getFiles()) {
          fileToNode.put(file, new PomNode(file));
        }

        updateFromRoot(true, true);
      }

      public void projectAdded(final VirtualFile file) {
        final PomNode newNode = new PomNode(file);
        fileToNode.put(file, newNode);
        root.addToStructure(newNode);

        updateFromRoot(true, true);
      }

      public void projectUpdated(VirtualFile file) {
        final PomNode pomNode = fileToNode.get(file);
        if (pomNode != null) {
          pomNode.onFileUpdate();
        }
        else {
          projectAdded(file);
        }
      }

      public void projectRemoved(VirtualFile file) {
        final PomNode pomNode = fileToNode.get(file);
        if (pomNode != null) {
          fileToNode.remove(file);
          pomNode.removeFromParent();
        }
      }

      public void setIgnored(VirtualFile file, boolean on) {
        final PomNode pomNode = fileToNode.get(file);
        if (pomNode != null) {
          pomNode.setIgnored(on);
        }
      }

      public void setProfiles(VirtualFile file, @NotNull Collection<String> profiles) {
        final PomNode pomNode = fileToNode.get(file);
        if (pomNode != null) {
          pomNode.setProfiles(profiles);
        }
      }

      public void attachPlugins(final VirtualFile file, @NotNull final Collection<MavenId> plugins) {
        final PomNode pomNode = fileToNode.get(file);
        if (pomNode != null) {
          pomNode.attachPlugins(plugins);
        }
      }

      public void detachPlugins(final VirtualFile file, @NotNull final Collection<MavenId> plugins) {
        final PomNode pomNode = fileToNode.get(file);
        if (pomNode != null) {
          pomNode.detachPlugins(plugins);
        }
      }
    });

    myEventsHandler.addListener( new MavenEventsHandler.Listener() {
      public void updateShortcuts(@Nullable String actionId) {
        for (PomNode pomNode : fileToNode.values()) {
          pomNode.updateShortcuts(actionId);
        }
      }
    });

    myEventsHandler.installTaskSelector(new MavenEventsHandler.TaskSelector() {

      SelectMavenGoalDialog dialog;

      public boolean select(final Project project, @Nullable final String pomPath, @Nullable final String goal, @NotNull final String title) {
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

    IdeaAPIHelper.installCheckboxRenderer(tree, new IdeaAPIHelper.CheckboxHandler() {
      public void toggle(final TreePath treePath, final InputEvent e) {
        final SimpleNode node = tree.getNodeFor(treePath);
        if (node != null) {
          node.handleDoubleClickOrEnter(tree, e);
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

  public PomTreeViewSettings getTreeViewSettings() {
    return settings;
  }

  public PomTreeViewSettings getState() {
    return settings;
  }

  public void loadState(PomTreeViewSettings state) {
    settings = state;
  }

  protected void updateTreeFrom(@Nullable SimpleNode node) {
    if (node != null) {
      final DefaultMutableTreeNode mutableTreeNode = TreeUtil.findNodeWithObject((DefaultMutableTreeNode)tree.getModel().getRoot(), node);
      if (mutableTreeNode != null) {
        treeBuilder.addSubtreeToUpdate(mutableTreeNode);
        return;
      }
    }
    updateFromRoot(node == root, false);
  }

  public void updateFromRoot(boolean rebuild, boolean restructure) {
    if (restructure) {
      root.rebuild(fileToNode.values());
    }
    treeBuilder.updateFromRoot(rebuild);
    if (rebuild) {
      tree.expandPath(new TreePath(tree.getModel().getRoot()));
    }
  }

  public void projectOpened() {
    final JPanel navigatorPanel = new MavenNavigatorPanel(project, myProjectsManager, tree);

    ToolWindow pomToolWindow = ToolWindowManager.getInstance(project)
      .registerToolWindow(MAVEN_NAVIGATOR_TOOLWINDOW_ID, navigatorPanel, ToolWindowAnchor.RIGHT, project);
    pomToolWindow.setIcon(myIcon);
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
}
