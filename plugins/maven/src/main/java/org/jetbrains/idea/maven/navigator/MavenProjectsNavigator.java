package org.jetbrains.idea.maven.navigator;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.utils.MavenIcons;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.SimpleProjectComponent;

import javax.swing.*;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@State(name = "MavenProjectNavigator", storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")})
public class MavenProjectsNavigator extends SimpleProjectComponent implements PersistentStateComponent<MavenProjectsNavigatorState> {
  public static final String TOOL_WINDOW_ID = "Maven Projects";

  private static final URL ADD_ICON_URL = MavenProjectsNavigator.class.getResource("/general/add.png");
  private static final URL SYNC_ICON_URL = MavenProjectsNavigator.class.getResource("/actions/sync.png");

  private final AtomicBoolean myInitialized = new AtomicBoolean(false);

  private MavenProjectsNavigatorState myState = new MavenProjectsNavigatorState();

  private final MavenProjectsManager myProjectsManager;
  private final MavenTasksManager myTasksManager;
  private final MavenShortcutsManager myShortcutsManager;

  private SimpleTree myTree;
  private MavenProjectsStructure myStructure;

  public static MavenProjectsNavigator getInstance(Project project) {
    return project.getComponent(MavenProjectsNavigator.class);
  }

  public MavenProjectsNavigator(Project project,
                                MavenProjectsManager projectsManager,
                                MavenTasksManager tasksManager,
                                MavenShortcutsManager shortcutsManager) {
    super(project);
    myProjectsManager = projectsManager;
    myTasksManager = tasksManager;
    myShortcutsManager = shortcutsManager;
  }

  public MavenProjectsNavigatorState getState() {
    return myState;
  }

  public void loadState(MavenProjectsNavigatorState state) {
    myState = state;
    update(true);
  }

  public boolean getGroupModules() {
    return myState.groupStructurally;
  }

  public void setGroupModules(boolean value) {
    if (myState.groupStructurally != value) {
      myState.groupStructurally = value;
      update(true);
    }
  }

  public boolean getShowIgnored() {
    return myState.showIgnored;
  }

  public void setShowIgnored(boolean value) {
    if (myState.showIgnored != value) {
      myState.showIgnored = value;
      update(true);
    }
  }

  public boolean getShowBasicPhasesOnly() {
    return myState.showBasicPhasesOnly;
  }

  public void setShowBasicPhasesOnly(boolean value) {
    if (myState.showBasicPhasesOnly != value) {
      myState.showBasicPhasesOnly = value;
      update(false);
    }
  }

  @Override
  public void initComponent() {
    if (!isNormalProject()) return;
    doInit();
  }

  @TestOnly
  public void initForTests() {
    doInit();
  }

  private void doInit() {
    if (myInitialized.getAndSet(true)) return;

    initTree();
    listenForActivation();

    if (isUnitTestMode()) return;

    MavenUtil.runWhenInitialized(myProject, new DumbAwareRunnable() {
      public void run() {
        initToolWindow();
      }
    });
  }

  @Override
  public void disposeComponent() {
    myInitialized.set(false);
  }

  private boolean isInitialized() {
    return myInitialized.get();
  }

  private void initTree() {
    myTree = new SimpleTree() {
      private final JLabel myLabel = new JLabel(ProjectBundle.message("maven.navigator.nothing.to.display",
                                                                      MavenUtil.formatHtmlImage(ADD_ICON_URL),
                                                                      MavenUtil.formatHtmlImage(SYNC_ICON_URL)));

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (myProjectsManager.hasProjects()) return;

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

    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
    myStructure = new MavenProjectsStructure(myProject, myProjectsManager, myTasksManager, myShortcutsManager, this, myTree);
  }

  private void listenForActivation() {
    myProjectsManager.addManagerListener(new MavenProjectsManager.Listener() {
      public void activated() {
        scheduleStructureUpdate(new Runnable() {
          public void run() {
            myStructure.buildTree();
          }
        });
        listenForProjectsChanges();
      }

      public void scheduledImportsChanged() {
      }
    });
  }

  private void listenForProjectsChanges() {
    myProjectsManager.addProjectsTreeListener(new MyProjectsListener());

    myShortcutsManager.addListener(new MavenShortcutsManager.Listener() {
      public void shortcutsUpdated() {
        scheduleStructureUpdate(new Runnable() {
          public void run() {
            myStructure.updateGoals();
          }
        });
      }
    });

    myTasksManager.addListener(new MavenTasksManager.Listener() {
      public void compileTasksChanged() {
        scheduleStructureUpdate(new Runnable() {
          public void run() {
            myStructure.updateGoals();
          }
        });
      }
    });
  }

  private void initToolWindow() {
    JPanel panel = new MavenProjectsNavigatorPanel(myProject, myTree);

    ToolWindowManager manager = ToolWindowManager.getInstance(myProject);
    ToolWindow toolWindow = manager.registerToolWindow(TOOL_WINDOW_ID, panel, ToolWindowAnchor.RIGHT, myProject, true);
    toolWindow.setIcon(MavenIcons.MAVEN_ICON);
  }

  private void update(boolean restructure) {
    if (!isInitialized()) return;
    myStructure.update(restructure);
  }

  public void selectInTree(MavenProject project) {
    if (!isInitialized()) return;
    myStructure.select(project);
  }

  @TestOnly
  public MavenProjectsStructure getStructureForTests() {
    return myStructure;
  }

  private void scheduleStructureUpdate(Runnable r) {
    MavenUtil.invokeLater(myProject, r);
  }

  private class MyProjectsListener extends MavenProjectsTree.ListenerAdapter {
    @Override
    public void projectsIgnoredStateChanged(final List<MavenProject> ignored, final List<MavenProject> unignored, Object message) {
      scheduleStructureUpdate(new Runnable() {
        public void run() {
          myStructure.updateIgnored(ContainerUtil.concat(ignored, unignored));
        }
      });
    }

    @Override
    public void profilesChanged(final List<String> profiles) {
      scheduleStructureUpdate(new Runnable() {
        public void run() {
          myStructure.setActiveProfiles(profiles);
        }
      });
    }

    @Override
    public void projectsUpdated(final List<Pair<MavenProject, MavenProjectChanges>> updated,
                                final List<MavenProject> deleted,
                                Object message) {
      scheduleStructureUpdate(new Runnable() {
        public void run() {
          myStructure.updateProjects(MavenUtil.collectFirsts(updated), deleted);
        }
      });
    }

    public void projectResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                org.apache.maven.project.MavenProject nativeMavenProject,
                                Object message) {
      updateProject(projectWithChanges.first);
    }

    public void pluginsResolved(MavenProject project) {
      updateProject(project);
    }

    private void updateProject(final MavenProject project) {
      scheduleStructureUpdate(new Runnable() {
        public void run() {
          myStructure.updateProjects(Collections.singletonList(project), Collections.EMPTY_LIST);
        }
      });
    }
  }
}
