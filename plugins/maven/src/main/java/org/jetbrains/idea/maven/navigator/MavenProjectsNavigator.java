// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.navigator;

import com.intellij.execution.RunManagerListener;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.treeView.TreeState;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerListener;
import com.intellij.ui.AppUIUtil;
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
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.Collections;
import java.util.List;

@State(name = "MavenProjectNavigator", storages = {
  @Storage(StoragePathMacros.PRODUCT_WORKSPACE_FILE),
  @Storage(value = StoragePathMacros.WORKSPACE_FILE, deprecated = true)
})
public final class MavenProjectsNavigator extends MavenSimpleProjectComponent implements PersistentStateComponent<MavenProjectsNavigatorState>, Disposable {
  public static final String TOOL_WINDOW_ID = "Maven";
  public static final String TOOL_WINDOW_PLACE_ID = "Maven tool window";

  private MavenProjectsNavigatorState myState = new MavenProjectsNavigatorState();

  private SimpleTree myTree;
  private MavenProjectsStructure myStructure;
  private ToolWindow myToolWindow;

  public static MavenProjectsNavigator getInstance(Project project) {
    return project.getService(MavenProjectsNavigator.class);
  }

  public MavenProjectsNavigator(@NotNull Project project) {
    super(project);
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
  public void loadState(@NotNull MavenProjectsNavigatorState state) {
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
  public void initializeComponent() {
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
    MavenProjectsManager.getInstance(myProject).addManagerListener(new MavenProjectsManager.Listener() {
      @Override
      public void activated() {
        AppUIUtil.invokeLaterIfProjectAlive(myProject, () -> initToolWindow());
        listenForProjectsChanges();
        scheduleStructureUpdate();
      }
    });
  }

  @Override
  public void dispose() {
    myToolWindow = null;
  }

  private void listenForProjectsChanges() {
    MavenProjectsManager.getInstance(myProject).addProjectsTreeListener(new MyProjectsListener());

    MavenShortcutsManager.getInstance(myProject).addListener(() -> scheduleStructureRequest(() -> myStructure.updateGoals()));

    MavenTasksManager.getInstance(myProject).addListener(new MavenTasksManager.Listener() {
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

    myToolWindow = ToolWindowManager.getInstance(myProject).registerToolWindow(TOOL_WINDOW_ID, false, ToolWindowAnchor.RIGHT, myProject, true);
    myToolWindow.setIcon(MavenIcons.ToolWindowMaven);
    final ContentFactory contentFactory = ServiceManager.getService(ContentFactory.class);
    final Content content = contentFactory.createContent(panel, "", false);
    ContentManager contentManager = myToolWindow.getContentManager();
    contentManager.addContent(content);
    contentManager.setSelectedContent(content, false);

    myProject.getMessageBus().connect(content).subscribe(ToolWindowManagerListener.TOPIC, new ToolWindowManagerListener() {
      boolean wasVisible = false;

      @Override
      public void stateChanged(@NotNull ToolWindowManager toolWindowManager) {
        if (myToolWindow.isDisposed()) return;
        boolean visible = myToolWindow.isVisible();
        if (!visible || wasVisible) {
          return;
        }
        scheduleStructureUpdate();
        wasVisible = true;
      }
    });

    ActionManager actionManager = ActionManager.getInstance();

    DefaultActionGroup group = new DefaultActionGroup();
    group.add(actionManager.getAction("Maven.GroupProjects"));
    group.add(actionManager.getAction("Maven.ShowIgnored"));
    group.add(actionManager.getAction("Maven.ShowBasicPhasesOnly"));
    group.add(actionManager.getAction("Maven.AlwaysShowArtifactId"));
    group.add(actionManager.getAction("Maven.ShowVersions"));

    ((ToolWindowEx)myToolWindow).setAdditionalGearActions(group);
  }

  private void initTree() {
    MavenProjectsManager mavenProjectManager = MavenProjectsManager.getInstance(myProject);
    myTree = new SimpleTree() {
      private final JTextPane myPane = new JTextPane();

      {
        myPane.setOpaque(false);
        String addIconText = "'+'";
        String refreshIconText = "'Reimport'";
        String message = MavenProjectBundle.message("maven.navigator.nothing.to.display", addIconText, refreshIconText);
        int firstEol = message.indexOf("\n");
        int addIconMarkerIndex = message.indexOf(addIconText);
        myPane.replaceSelection(message.substring(0, addIconMarkerIndex));
        myPane.insertIcon(AllIcons.General.Add);
        int refreshIconMarkerIndex = message.indexOf(refreshIconText);
        myPane.replaceSelection(message.substring(addIconMarkerIndex + addIconText.length(), refreshIconMarkerIndex));
        myPane.insertIcon(AllIcons.Actions.Refresh);
        myPane.replaceSelection(message.substring(refreshIconMarkerIndex + refreshIconText.length()));

        StyledDocument document = myPane.getStyledDocument();
        SimpleAttributeSet centerAlignment = new SimpleAttributeSet();
        StyleConstants.setAlignment(centerAlignment, StyleConstants.ALIGN_CENTER);
        SimpleAttributeSet justifiedAlignment = new SimpleAttributeSet();
        StyleConstants.setAlignment(justifiedAlignment, StyleConstants.ALIGN_JUSTIFIED);

        document.setParagraphAttributes(0, firstEol, centerAlignment, false);
        document.setParagraphAttributes(firstEol + 2, document.getLength(), justifiedAlignment, false);
      }

      @Override
      protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        if (mavenProjectManager.hasProjects()) {
          return;
        }

        myPane.setFont(getFont());
        myPane.setBackground(getBackground());
        myPane.setForeground(getForeground());
        Rectangle bounds = getBounds();
        myPane.setBounds(0, 0, bounds.width - 10, bounds.height);

        Graphics g2 = g.create(bounds.x + 10, bounds.y + 20, bounds.width, bounds.height);
        try {
          myPane.paint(g2);
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
      boolean hasMavenProjects = !MavenProjectsManager.getInstance(myProject).getProjects().isEmpty();

      if (myToolWindow.isAvailable() != hasMavenProjects) {
        myToolWindow.setAvailable(hasMavenProjects);

        if (hasMavenProjects) {
          myToolWindow.activate(null);
        }
      }

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
    myStructure = new MavenProjectsStructure(myProject, MavenProjectsManager.getInstance(myProject), MavenTasksManager.getInstance(myProject), MavenShortcutsManager.getInstance(myProject), this, myTree);
  }

  private void scheduleStructureUpdate() {
    scheduleStructureRequest(() -> myStructure.update());
  }

  private final class MyProjectsListener implements MavenProjectsTree.Listener {
    @Override
    public void projectsIgnoredStateChanged(@NotNull final List<MavenProject> ignored, @NotNull final List<MavenProject> unignored, boolean fromImport) {
      scheduleStructureRequest(() -> myStructure.updateIgnored(ContainerUtil.concat(ignored, unignored)));
    }

    @Override
    public void profilesChanged() {
      scheduleStructureRequest(() -> myStructure.updateProfiles());
    }

    @Override
    public void projectsUpdated(@NotNull List<Pair<MavenProject, MavenProjectChanges>> updated, @NotNull List<MavenProject> deleted) {
      scheduleUpdateProjects(MavenUtil.collectFirsts(updated), deleted);
    }

    @Override
    public void projectResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                NativeMavenProjectHolder nativeMavenProject) {
      scheduleUpdateProjects(Collections.singletonList(projectWithChanges.first), Collections.emptyList());
    }

    @Override
    public void pluginsResolved(@NotNull MavenProject project) {
      scheduleUpdateProjects(Collections.singletonList(project), Collections.emptyList());
    }

    private void scheduleUpdateProjects(final List<MavenProject> projects, final List<MavenProject> deleted) {
      scheduleStructureRequest(() -> myStructure.updateProjects(projects, deleted));
    }
  }
}
