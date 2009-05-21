package org.jetbrains.idea.maven.tasks;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.ex.KeymapManagerEx;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.runner.MavenRunner;
import org.jetbrains.idea.maven.runner.MavenRunnerParameters;
import org.jetbrains.idea.maven.utils.MavenDataKeys;
import org.jetbrains.idea.maven.utils.MavenMergingUpdateQueue;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jetbrains.idea.maven.utils.SimpleProjectComponent;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@State(name = "MavenEventsHandler", storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")})
public class MavenTasksManager extends SimpleProjectComponent implements PersistentStateComponent<MavenTasksManagerState> {
  private static final String ACTION_ID_PREFIX = "Maven_";

  private static final String BEFORE_MAKE = TasksBundle.message("maven.event.text.before.make");
  private static final String AFTER_MAKE = TasksBundle.message("maven.event.text.after.make");
  private static final String BEFORE_RUN = TasksBundle.message("maven.event.text.before.run");

  private final AtomicBoolean isInitialized = new AtomicBoolean();

  private final MavenProjectsManager myProjectsManager;
  private final MavenRunner myRunner;

  private MavenTasksManagerState myState = new MavenTasksManagerState();
  private Map<Pair<String, Integer>, MavenGoalTask> myBeforeRunMap = new THashMap<Pair<String, Integer>, MavenGoalTask>();

  private MyKeymapListener myKeymapListener;
  private final List<Listener> myListeners = ContainerUtil.createEmptyCOWList();
  private TaskSelector myTaskSelector;

  public static MavenTasksManager getInstance(Project project) {
    return project.getComponent(MavenTasksManager.class);
  }

  public MavenTasksManager(Project project, MavenProjectsManager projectsManager, MavenRunner runner) {
    super(project);
    myProjectsManager = projectsManager;
    myRunner = runner;
  }

  @Override
  public void initComponent() {
    if (!isNormalProject()) return;

    MavenUtil.runWhenInitialized(myProject, new DumbAwareRunnable() {
      public void run() {
        doInit();
      }
    });
  }

  @TestOnly
  public void doInit() {
    if (isInitialized.getAndSet(true)) return;

    MyProjectsTreeListener listener = new MyProjectsTreeListener();
    myProjectsManager.addManagerListener(listener);
    myProjectsManager.addProjectsTreeListener(listener);

    myKeymapListener = new MyKeymapListener();

    CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    compilerManager.addBeforeTask(new CompileTask() {
      public boolean execute(CompileContext context) {
        return MavenTasksManager.this.execute(getState().beforeCompile, context.getProgressIndicator());
      }
    });
    compilerManager.addAfterTask(new CompileTask() {
      public boolean execute(CompileContext context) {
        return MavenTasksManager.this.execute(getState().afterCompile, context.getProgressIndicator());
      }
    });
  }

  @Override
  public void disposeComponent() {
    if (!isInitialized.getAndSet(false)) return;

    myKeymapListener.stopListen();
    MavenKeymapExtension.clearActions(myProject);
  }

  @NotNull
  public MavenTasksManagerState getState() {
    Map<String, MavenGoalTask> map = new THashMap<String, MavenGoalTask>();

    for (Map.Entry<Pair<String, Integer>, MavenGoalTask> each : myBeforeRunMap.entrySet()) {
      Pair<String, Integer> key = each.getKey();
      String type = key.first;
      Integer configID = key.second;
      MavenGoalTask task = each.getValue();

      if (configID == null) {
        map.put(type, task);
      }
      else {
        RunManagerImpl runManager = (RunManagerImpl)RunManagerImpl.getInstance(myProject);
        RunConfiguration config = runManager.getConfigurationByUniqueID(configID);
        if (config != null) {
          map.put(type + "#" + config.getName(), task);
        }
      }
    }
    myState.beforeRun = map;
    return myState;
  }

  public void loadState(MavenTasksManagerState state) {
    Map<Pair<String, Integer>, MavenGoalTask> map = new THashMap<Pair<String, Integer>, MavenGoalTask>();

    for (Map.Entry<String, MavenGoalTask> each : state.beforeRun.entrySet()) {
      String key = each.getKey();
      MavenGoalTask task = each.getValue();
      int delimIndex = key.indexOf("#");
      if (delimIndex == -1) { // configurationType
        map.put(new Pair<String, Integer>(key, null), task);
      }
      else {
        String type = key.substring(0, delimIndex);
        String name = key.substring(delimIndex + 1);

        RunManagerImpl runManager = (RunManagerImpl)RunManagerImpl.getInstance(myProject);
        RunConfiguration config = runManager.getConfigurationByUniqueName(type + "." + name);
        if (config != null) {
          map.put(new Pair<String, Integer>(type, config.getUniqueID()), task);
        }
      }
    }
    myState = state;
    myBeforeRunMap = map;
  }

  public boolean execute(@NotNull Collection<MavenGoalTask> mavenTasks, ProgressIndicator indicator) {
    final List<MavenRunnerParameters> parametersList = new ArrayList<MavenRunnerParameters>();
    for (MavenGoalTask mavenTask : mavenTasks) {
      final MavenRunnerParameters runnerParameters = mavenTask.createRunnerParameters(myProjectsManager);
      if (runnerParameters == null) {
        return false;
      }
      parametersList.add(runnerParameters);
    }
    return myRunner.runBatch(parametersList, null, null, TasksBundle.message("maven.event.executing"), indicator);
  }

  public String getActionId(@Nullable String projectPath, @Nullable String goal) {
    StringBuilder result = new StringBuilder(ACTION_ID_PREFIX);
    result.append(myProject.getLocationHash());

    if (projectPath != null) {
      String portablePath = FileUtil.toSystemIndependentName(projectPath);

      result.append(new File(portablePath).getParentFile().getName());
      result.append(Integer.toHexString(portablePath.hashCode()));

      if (goal != null) result.append(goal);
    }

    return result.toString();
  }

  public String getActionDescription(@NotNull String pomPath, @NotNull String goal) {
    String actionId = getActionId(pomPath, goal);
    if (actionId == null) return null;

    String shortcutsString = getShortcutString(actionId);
    MavenGoalTask mavenTask = new MavenGoalTask(pomPath, goal);

    StringBuilder stringBuilder = new StringBuilder();
    appendIf(stringBuilder, shortcutsString, shortcutsString != null);
    appendIf(stringBuilder, BEFORE_MAKE, myState.beforeCompile.contains(mavenTask));
    appendIf(stringBuilder, AFTER_MAKE, myState.afterCompile.contains(mavenTask));
    appendIf(stringBuilder, BEFORE_RUN, hasAssginments(mavenTask));

    return stringBuilder.length() == 0 ? null : stringBuilder.toString();
  }

  private static String getShortcutString(String actionId) {
    Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
    Shortcut[] shortcuts = activeKeymap.getShortcuts(actionId);
    if (shortcuts == null || shortcuts.length == 0) return null;

    return KeymapUtil.getShortcutsText(shortcuts);
  }

  private void appendIf(StringBuilder stringBuilder, String text, boolean condition) {
    if (!condition) return;

    if (stringBuilder.length() > 0) {
      stringBuilder.append(", ");
    }
    stringBuilder.append(text);
  }

  public void installTaskSelector(TaskSelector selector) {
    myTaskSelector = selector;
  }

  @Nullable
  private MavenGoalTask selectMavenTask(Project project, RunConfiguration runConfiguration) {
    if (myTaskSelector == null) {
      return null;
    }

    final MavenGoalTask mavenTask = getTask(runConfiguration.getType(), runConfiguration);

    if (!myTaskSelector.select(project, mavenTask != null ? mavenTask.pomPath : null, mavenTask != null ? mavenTask.goal : null,
                               TasksBundle.message("maven.event.select.goal.title"))) {
      return mavenTask;
    }

    final String pomPath = myTaskSelector.getSelectedPomPath();
    if (pomPath != null) {
      final MavenGoalTask newMavenTask = new MavenGoalTask(pomPath, myTaskSelector.getSelectedGoal());
      if (!isAssignedForType(runConfiguration.getType(), newMavenTask)) {
        assignTask(runConfiguration.getType(), runConfiguration, newMavenTask);
        fireTaskShortcutsUpdated(newMavenTask);
      }
      return newMavenTask;
    }

    clearAssignment(runConfiguration.getType(), runConfiguration);
    fireShortcutsUpdated();
    return null;
  }

  public String configureRunStep(RunConfiguration runConfiguration) {
    return getDescription(selectMavenTask(myProject, runConfiguration));
  }

  public String getRunStepDescription(RunConfiguration runConfiguration) {
    return getDescription(getTask(runConfiguration.getType(), runConfiguration));
  }

  @Nullable
  private String getDescription(MavenGoalTask mavenTask) {
    if (mavenTask == null) return null;

    VirtualFile file = LocalFileSystem.getInstance().findFileByPath(mavenTask.pomPath);
    if (file == null) return null;

    MavenProject project = myProjectsManager.findProject(file);
    if (project == null) return null;

    return project.getDisplayName() + ":" + mavenTask.goal;
  }

  public MavenGoalTask getAssignedTask(ConfigurationType type, RunConfiguration config) {
    return myBeforeRunMap.get(getKey(type, config));
  }

  MavenGoalTask getTask(ConfigurationType type, RunConfiguration config) {
    final MavenGoalTask task = getAssignedTask(type, config);
    return task != null ? task : getAssignedTask(type, null);
  }

  public boolean hasAssginments(MavenGoalTask task) {
    return myBeforeRunMap.containsValue(task);
  }

  public boolean isAssignedForType(ConfigurationType type, MavenGoalTask mavenTask) {
    return mavenTask.equals(getAssignedTask(type, null));
  }

  public void assignTask(ConfigurationType type, RunConfiguration config, MavenGoalTask mavenTask) {
    myBeforeRunMap.put(getKey(type, config), mavenTask);
  }

  public void clearAssignment(ConfigurationType type, RunConfiguration config) {
    myBeforeRunMap.remove(getKey(type, config));
  }

  public void clearAssignments(MavenGoalTask task) {
    List<Pair<String, Integer>> keysToRemove = new ArrayList<Pair<String, Integer>>();

    for (Map.Entry<Pair<String, Integer>, MavenGoalTask> each : myBeforeRunMap.entrySet()) {
      if (each.getValue().equals(task)) {
        keysToRemove.add(each.getKey());
      }
    }

    for (Pair<String, Integer> each : keysToRemove) {
      myBeforeRunMap.remove(each);
    }
  }

  static Pair<String, Integer> getKey(ConfigurationType type, RunConfiguration configuration) {
    if (configuration != null) {
      return new Pair<String, Integer>(type.getDisplayName(), configuration.getUniqueID());
    }
    else {
      return new Pair<String, Integer>(type.getDisplayName(), null);
    }
  }

  @Nullable
  public static MavenGoalTask getMavenTask(DataContext dataContext) {
    if (dataContext == null) return null;

    VirtualFile file = MavenUtil.getMavenProjectFileFromContext(dataContext);
    if (file == null) return null;

    List<String> goals = MavenDataKeys.MAVEN_GOALS.getData(dataContext);
    if (goals == null || goals.size() != 1) return null;

    return new MavenGoalTask(file.getPath(), goals.get(0));
  }

  private void fireShortcutsUpdated() {
    for (Listener listener : myListeners) {
      listener.shortcutsUpdated();
    }
  }

  public void fireTaskShortcutsUpdated(@NotNull MavenGoalTask mavenTask) {
    fireShortcutsUpdated();
  }

  public void addListener(Listener listener) {
    myListeners.add(listener);
  }

  public interface Listener {
    void shortcutsUpdated();
  }

  public interface TaskSelector {
    boolean select(Project project, @Nullable String pomPath, @Nullable String goal, @NotNull String title);

    String getSelectedPomPath();

    String getSelectedGoal();
  }

  private class MyKeymapListener implements KeymapManagerListener, Keymap.Listener {
    private Keymap myCurrentKeymap = null;

    public MyKeymapListener() {
      KeymapManager keymapManager = KeymapManager.getInstance();
      listenTo(keymapManager.getActiveKeymap());
      keymapManager.addKeymapManagerListener(this);
    }

    public void activeKeymapChanged(Keymap keymap) {
      listenTo(keymap);
      fireShortcutsUpdated();
    }

    private void listenTo(Keymap keymap) {
      if (myCurrentKeymap != null) {
        myCurrentKeymap.removeShortcutChangeListener(this);
      }
      myCurrentKeymap = keymap;
      if (myCurrentKeymap != null) {
        myCurrentKeymap.addShortcutChangeListener(this);
      }
    }

    public void onShortcutChanged(String actionId) {
      fireShortcutsUpdated();
    }

    public void stopListen() {
      listenTo(null);
      KeymapManagerEx.getInstanceEx().removeKeymapManagerListener(this);
    }
  }

  private class MyProjectsTreeListener extends MavenProjectsTree.ListenerAdapter implements MavenProjectsManager.Listener {
    private final Map<MavenProject, Boolean> mySheduledProjects = new THashMap<MavenProject, Boolean>();
    private final MergingUpdateQueue myUpdateQueue = new MavenMergingUpdateQueue(getComponentName() + ": Keymap Update",
                                                                                 500, true, myProject);

    public void activated() {
      scheduleKeymapUpdate(myProjectsManager.getNonIgnoredProjects(), true);
    }

    @Override
    public void projectsIgnoredStateChanged(List<MavenProject> ignored, List<MavenProject> unignored) {
      scheduleKeymapUpdate(unignored, true);
      scheduleKeymapUpdate(ignored, false);
    }

    @Override
    public void projectsUpdated(List<MavenProject> updated, List<MavenProject> deleted) {
      scheduleKeymapUpdate(updated, true);
      scheduleKeymapUpdate(deleted, false);
    }

    @Override
    public void projectResolved(MavenProject project, org.apache.maven.project.MavenProject nativeMavenProject) {
      scheduleKeymapUpdate(Collections.singletonList(project), true);
    }

    @Override
    public void pluginsResolved(MavenProject project) {
      scheduleKeymapUpdate(Collections.singletonList(project), true);
    }

    private void scheduleKeymapUpdate(List<MavenProject> mavenProjects, boolean forUpdate) {
      synchronized (mySheduledProjects) {
        for (MavenProject each : mavenProjects) {
          mySheduledProjects.put(each, forUpdate);
        }
      }

      myUpdateQueue.queue(new Update(MavenTasksManager.this) {
        public void run() {
          List<MavenProject> projectToUpdate;
          List<MavenProject> projectToDelete;
          synchronized (mySheduledProjects) {
            projectToUpdate = selectScheduledProjects(true);
            projectToDelete = selectScheduledProjects(false);
            mySheduledProjects.clear();
          }
          MavenKeymapExtension.clearActions(myProject, projectToDelete);
          MavenKeymapExtension.updateActions(myProject, projectToUpdate);
        }
      });
    }

    private List<MavenProject> selectScheduledProjects(final boolean forUpdate) {
      return ContainerUtil.mapNotNull(mySheduledProjects.entrySet(), new Function<Map.Entry<MavenProject, Boolean>, MavenProject>() {
        public MavenProject fun(Map.Entry<MavenProject, Boolean> eachEntry) {
          return forUpdate == eachEntry.getValue() ? eachEntry.getKey() : null;
        }
      });
    }
  }
}
