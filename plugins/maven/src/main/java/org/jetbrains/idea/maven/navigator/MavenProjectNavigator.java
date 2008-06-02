package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.ui.treeStructure.SimpleTreeBuilder;
import com.intellij.util.Function;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.core.util.IdeaAPIHelper;
import org.jetbrains.idea.maven.events.MavenEventsHandler;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.project.MavenProjectModelManager;
import org.jetbrains.idea.maven.project.MavenProjectModelProblem;
import org.jetbrains.idea.maven.project.ProjectBundle;
import org.jetbrains.idea.maven.repository.MavenPluginsRepository;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@State(name = "MavenProjectNavigator", storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")})
public class MavenProjectNavigator extends MavenTreeStructure implements ProjectComponent, PersistentStateComponent<PomTreeViewSettings> {
  private boolean isInitialized = false;

  public static MavenProjectNavigator getInstance(Project project) {
    return project.getComponent(MavenProjectNavigator.class);
  }

  @NonNls private static final String MAVEN_NAVIGATOR_TOOLWINDOW_ID = "Maven projects";

  private final Icon myIcon = IconLoader.getIcon("/images/mavenEmblem.png");

  private PomTreeViewSettings settings = new PomTreeViewSettings();

  private SimpleTreeBuilder treeBuilder;
  private SimpleTree tree;

  private Map<VirtualFile, PomNode> fileToNode = new LinkedHashMap<VirtualFile, PomNode>();


  public MavenProjectNavigator(final Project project,
                               MavenProjectsManager projectsManager,
                               MavenPluginsRepository repository,
                               MavenEventsHandler eventsHandler) {
    super(project, projectsManager, repository, eventsHandler);

    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    isInitialized = true;

    tree = new SimpleTree() {
      private JLabel myLabel = new JLabel(ProjectBundle.message("maven.please.reimport"));

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (MavenProjectsManager.getInstance(project).isMavenizedProject()) return;

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

    tree.addMouseMotionListener(new MouseMotionListener() {
      public void mouseDragged(MouseEvent e) {
      }

      public void mouseMoved(MouseEvent e) {
        TreePath p = tree.getPathForLocation(e.getX(), e.getY());
        if (p != null) {
          Object last = p.getLastPathComponent();
          Object object = ((DefaultMutableTreeNode)last).getUserObject();
          if (object instanceof PomNode) {
            PomNode pomNode = (PomNode)object;
            showProblemsToolTip(pomNode);
          }
        }
      }
    });

    treeBuilder = new SimpleTreeBuilder(tree, (DefaultTreeModel)tree.getModel(), this, null);
    treeBuilder.initRoot();
    Disposer.register(project, treeBuilder);

    myProjectsManager.addListener(new MavenProjectsManager.Listener() {
      public void activate() {
        for (MavenProjectModel each : myProjectsManager.getProjects()) {
          fileToNode.put(each.getFile(), new PomNode(each));
        }

        updateFromRoot(true, true);
      }

      public void setIgnored(VirtualFile file, boolean on) {
        final PomNode pomNode = fileToNode.get(file);
        if (pomNode != null) {
          pomNode.setIgnored(on);
        }
      }

      public void profilesChanged(List<String> profiles) {
        for (PomNode each : fileToNode.values()) {
          each.setActiveProfiles(profiles);
        }
        tree.repaint();
      }
    });

    myProjectsManager.getMavenProjectModelManager().addListener(new MavenProjectModelManager.Listener() {
      public void projectAdded(final MavenProjectModel n) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            final PomNode newNode = new PomNode(n);
            fileToNode.put(n.getFile(), newNode);
            root.addToStructure(newNode);

            updateFromRoot(true, true);
            tree.repaint();
          }
        });
      }

      public void projectUpdated(final MavenProjectModel n) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            final PomNode pomNode = fileToNode.get(n.getFile());
            if (pomNode != null) {
              pomNode.onFileUpdate();
            }
            else {
              projectAdded(n);
            }
            tree.repaint();
          }
        });
      }

      public void projectRemoved(final MavenProjectModel n) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            final PomNode pomNode = fileToNode.get(n.getFile());
            if (pomNode != null) {
              fileToNode.remove(n.getFile());
              pomNode.removeFromParent();
              tree.repaint();
            }
          }
        });
      }
    });

    myEventsHandler.addListener(new MavenEventsHandler.Listener() {
      public void updateShortcuts(@Nullable String actionId) {
        for (PomNode pomNode : fileToNode.values()) {
          pomNode.updateShortcuts(actionId);
        }
      }
    });

    myEventsHandler.installTaskSelector(new MavenEventsHandler.TaskSelector() {

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

  private void showProblemsToolTip(PomNode pomNode) {
    List<MavenProjectModelProblem> problems = pomNode.getMavenProjectModel().getProblems();
    if (problems.isEmpty()) {
      tree.setToolTipText(null);
      return;
    }

    String s = StringUtil.join(problems, new Function<MavenProjectModelProblem, String>() {
      public String fun(MavenProjectModelProblem each) {
        URL imageUrl = each.isCritical()
                        ? getClass().getResource("/images/error.png")
                        : getClass().getResource("/images/warning.png");
        return "<img src=\"" + imageUrl + "\"> " + each.getDescription();
      }
    }, "<br>");
    tree.setToolTipText("<html>" + s + "</html>");
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
    if (!isInitialized) return;

    final JPanel navigatorPanel = new MavenNavigatorPanel(myProject, myProjectsManager, tree);

    ToolWindow pomToolWindow = ToolWindowManager.getInstance(myProject)
      .registerToolWindow(MAVEN_NAVIGATOR_TOOLWINDOW_ID, navigatorPanel, ToolWindowAnchor.RIGHT, myProject);
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
