/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.navigator;

import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.treeStructure.SimpleTree;
import com.intellij.util.containers.ContainerUtil;
import icons.MavenIcons;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.execution.MavenRunnerSettings;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.tasks.MavenShortcutsManager;
import org.jetbrains.idea.maven.tasks.MavenTasksManager;
import org.jetbrains.idea.maven.utils.MavenLog;
import org.jetbrains.idea.maven.utils.MavenSimpleProjectComponent;
import org.jetbrains.idea.maven.utils.MavenUtil;

import javax.swing.*;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.net.URL;
import java.util.Collections;
import java.util.List;

@State(name = "MavenProjectNavigator", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class MavenProjectsNavigator extends MavenSimpleProjectComponent implements PersistentStateComponent<MavenProjectsNavigatorState>,
                                                                                   Disposable, ProjectComponent {
  public static final String TOOL_WINDOW_ID = "Maven Projects";

  private static final URL ADD_ICON_URL = MavenProjectsNavigator.class.getResource("/general/add.png");
  private static final URL SYNC_ICON_URL = MavenProjectsNavigator.class.getResource("/actions/refresh.png");

  private MavenProjectsNavigatorState myState = new MavenProjectsNavigatorState();

  private MavenProjectsManager myProjectsManager;
  private MavenTasksManager myTasksManager;
  private MavenShortcutsManager myShortcutsManager;

  private SimpleTree myTree;
  private MavenProjectsStructure myStructure;
  private ToolWindowEx myToolWindow;

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

  @Override
  public MavenProjectsNavigatorState getState() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (myStructure != null) {
      try {
        myState.treeState = new Element("root");
        TreeState.createOn(myTree).writeExternal(myState.treeState);
      }
      catch (WriteExternalException e) {
        MavenLog.LOG.warn(e);
      }
    }
    return myState;
  }

  @Override
  public void loadState(MavenProjectsNavigatorState state) {
    myState = state;
    scheduleStructureUpdate();
  }

  public boolean getGroupModules() {
    return myState.groupStructurally;
  }

  public void setGroupModules(boolean value) {
    if (myState.groupStructurally != value) {
      myState.groupStructurally = value;
      scheduleStructureUpdate();
    }
  }

  public boolean getShowIgnored() {
    return myState.showIgnored;
  }

  public void setShowIgnored(boolean value) {
    if (myState.showIgnored != value) {
      myState.showIgnored = value;
      scheduleStructureUpdate();
    }
  }

  public boolean getShowBasicPhasesOnly() {
    return myState.showBasicPhasesOnly;
  }

  public void setShowBasicPhasesOnly(boolean value) {
    if (myState.showBasicPhasesOnly != value) {
      myState.showBasicPhasesOnly = value;
      scheduleStructureUpdate();
    }
  }

  public boolean getAlwaysShowArtifactId() {
    return myState.alwaysShowArtifactId;
  }

  public void setAlwaysShowArtifactId(boolean value) {
    if (myState.alwaysShowArtifactId != value) {
      myState.alwaysShowArtifactId = value;
      scheduleStructureUpdate();
    }
  }

  public boolean getShowVersions() {
    return myState.showVersions;
  }

  public void setShowVersions(boolean value) {
    if (myState.showVersions != value) {
      myState.showVersions = value;
      scheduleStructureUpdate();
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
    initTree();
    initStructure();
  }

  private void doInit() {
    listenForProjectsChanges();
    if (isUnitTestMode()) return;
    MavenUtil.runWhenInitialized(myProject, (DumbAwareRunnable)() -> {
      if (myProject.isDisposed()) return;
      initToolWindow();
    });
  }

  @Override
  public void dispose() {
    myToolWindow = null;
    myProjectsManager = null;
  }

  private void listenForProjectsChanges() {
    myProjectsManager.addProjectsTreeListener(new MyProjectsListener());

    myShortcutsManager.addListener(new MavenShortcutsManager.Listener() {
      @Override
      public void shortcutsUpdated() {
        scheduleStructureRequest(() -> myStructure.updateGoals());
      }
    });

    myTasksManager.addListener(new MavenTasksManager.Listener() {
      @Override
      public void compileTasksChanged() {
        scheduleStructureRequest(() -> myStructure.updateGoals());
      }
    });

    MavenRunner.getInstance(myProject).getSettings().addListener(new MavenRunnerSettings.Listener() {
      @Override
      public void skipTestsChanged() {
        scheduleStructureRequest(() -> myStructure.updateGoals());
      }
    });

    myProject.getMessageBus().connect().subscribe(RunManagerListener.TOPIC, new RunManagerListener() {
      private void changed() {
        scheduleStructureRequest(() -> myStructure.updateRunConfigurations());
      }

      @Override
      public void runConfigurationAdded(@NotNull RunnerAndConfigurationSettings settings) {
        changed();
      }

      @Override
      public void runConfigurationRemoved(@NotNull RunnerAndConfigurationSettings settings) {
        changed();
      }

      @Override
      public void runConfigurationChanged(@NotNull RunnerAndConfigurationSettings settings) {
        changed();
      }

      @Override
      public void beforeRunTasksChanged() {
        scheduleStructureRequest(() -> myStructure.updateGoals());
      }
    });
  }

  private void initToolWindow() {
    initTree();
    JPanel panel = new MavenProjectsNavigatorPanel(myProject, myTree);

    AnAction removeAction = EmptyAction.wrap(ActionManager.getInstance().getAction("Maven.RemoveRunConfiguration"));
    removeAction.registerCustomShortcutSet(CommonShortcuts.getDelete(), myTree, myProject);
    AnAction editSource = EmptyAction.wrap(ActionManager.getInstance().getAction("Maven.EditRunConfiguration"));
    editSource.registerCustomShortcutSet(CommonShortcuts.getEditSource(), myTree, myProject);

    final ToolWindowManagerEx manager = ToolWindowManagerEx.getInstanceEx(myProject);
    myToolWindow = (ToolWindowEx)manager.registerToolWindow(TOOL_WINDOW_ID, false, ToolWindowAnchor.RIGHT, myProject, true);
    myToolWindow.setIcon(MavenIcons.ToolWindowMaven);
    final ContentFactory contentFactory = ServiceManager.getService(ContentFactory.class);
    final Content content = contentFactory.createContent(panel, "", false);
    ContentManager contentManager = myToolWindow.getContentManager();
    contentManager.addContent(content);
    contentManager.setSelectedContent(content, false);

    final ToolWindowManagerAdapter listener = new ToolWindowManagerAdapter() {
      boolean wasVisible = false;

      @Override
      public void stateChanged() {
        if (myToolWindow.isDisposed()) return;
        boolean visible = myToolWindow.isVisible();
        if (!visible || wasVisible) {
          return;
        }
        scheduleStructureUpdate();
        wasVisible = true;
      }
    };
    manager.addToolWindowManagerListener(listener, myProject);

    ActionManager actionManager = ActionManager.getInstance();

    DefaultActionGroup group = new DefaultActionGroup();
    group.add(actionManager.getAction("Maven.GroupProjects"));
    group.add(actionManager.getAction("Maven.ShowIgnored"));
    group.add(actionManager.getAction("Maven.ShowBasicPhasesOnly"));
    group.add(actionManager.getAction("Maven.AlwaysShowArtifactId"));
    group.add(actionManager.getAction("Maven.ShowVersions"));

    myToolWindow.setAdditionalGearActions(group);
  }

  private void initTree() {
    myTree = new SimpleTree() {
      private final JLabel myLabel = new JLabel(
        ProjectBundle.message("maven.navigator.nothing.to.display", MavenUtil.formatHtmlImage(ADD_ICON_URL),
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
    myTree.getEmptyText().clear();

    myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
  }

  @TestOnly
  public MavenProjectsStructure getStructureForTests() {
    return myStructure;
  }

  public void selectInTree(final MavenProject project) {
    scheduleStructureRequest(() -> myStructure.select(project));
  }

  private void scheduleStructureRequest(final Runnable r) {
    if (isUnitTestMode()) {
      if (myStructure != null) {
        r.run();
      }
      return;
    }

    if (myToolWindow == null) return;
    MavenUtil.invokeLater(myProject, () -> {
      if (!myToolWindow.isVisible()) return;

      boolean shouldCreate = myStructure == null;
      if (shouldCreate) {
        initStructure();
      }

      r.run();

      if (shouldCreate) {
        TreeState.createFrom(myState.treeState).applyTo(myTree);
      }
    });
  }

  private void initStructure() {
    myStructure = new MavenProjectsStructure(myProject, myProjectsManager, myTasksManager, myShortcutsManager, this, myTree);
  }

  private void scheduleStructureUpdate() {
    scheduleStructureRequest(() -> myStructure.update());
  }

  private class MyProjectsListener implements MavenProjectsManager.Listener, MavenProjectsTree.Listener {
    @Override
    public void activated() {
      scheduleStructureUpdate();
    }

    @Override
    public void projectsIgnoredStateChanged(final List<MavenProject> ignored, final List<MavenProject> unignored, boolean fromImport) {
      scheduleStructureRequest(() -> myStructure.updateIgnored(ContainerUtil.concat(ignored, unignored)));
    }

    @Override
    public void profilesChanged() {
      scheduleStructureRequest(() -> myStructure.updateProfiles());
    }

    @Override
    public void projectsUpdated(List<Pair<MavenProject, MavenProjectChanges>> updated, List<MavenProject> deleted) {
      scheduleUpdateProjects(MavenUtil.collectFirsts(updated), deleted);
    }

    @Override
    public void projectResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                NativeMavenProjectHolder nativeMavenProject) {
      scheduleUpdateProjects(Collections.singletonList(projectWithChanges.first), Collections.emptyList());
    }

    @Override
    public void pluginsResolved(MavenProject project) {
      scheduleUpdateProjects(Collections.singletonList(project), Collections.emptyList());
    }

    private void scheduleUpdateProjects(final List<MavenProject> projects, final List<MavenProject> deleted) {
      scheduleStructureRequest(() -> myStructure.updateProjects(projects, deleted));
    }
  }
}
