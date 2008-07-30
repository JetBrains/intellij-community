package org.jetbrains.idea.maven.events;

import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.core.MavenDataKeys;
import org.jetbrains.idea.maven.core.util.DummyProjectComponent;
import org.jetbrains.idea.maven.project.MavenConstants;
import org.jetbrains.idea.maven.project.MavenProjectModel;
import org.jetbrains.idea.maven.runner.MavenRunner;
import org.jetbrains.idea.maven.runner.executor.MavenRunnerParameters;
import org.jetbrains.idea.maven.state.MavenProjectsManager;

import java.io.File;
import java.util.*;

/**
 * @author Vladislav.Kaznacheev
 */
@State(name = "MavenEventsHandler", storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")})
public class MavenEventsManager extends DummyProjectComponent implements PersistentStateComponent<MavenEventsState> {
  public static MavenEventsManager getInstance(Project project) {
    return project.getComponent(MavenEventsManager.class);
  }

  @NonNls private static final String ACTION_ID_PREFIX = "Maven_";

  public static final String RUN_MAVEN_STEP = EventsBundle.message("maven.event.before.run");

  private static final String BEFORE_MAKE = EventsBundle.message("maven.event.text.before.make");
  private static final String AFTER_MAKE = EventsBundle.message("maven.event.text.after.make");
  private static final String BEFORE_RUN = EventsBundle.message("maven.event.text.before.run");

  private final Project myProject;
  private final MavenProjectsManager myProjectsManager;
  private final MavenRunner myRunner;

  private MavenEventsState myState = new MavenEventsState();
  private Map<Pair<String, Integer>, MavenTask> myBeforeRunMap = new HashMap<Pair<String, Integer>, MavenTask>();

  private MyKeymapListener myKeymapListener;
  private Collection<Listener> myListeners = new HashSet<Listener>();
  private TaskSelector myTaskSelector;

  private final Alarm myKeymapUpdaterAlarm = new Alarm(Alarm.ThreadToUse.OWN_THREAD);

  public MavenEventsManager(Project project, MavenProjectsManager projectsManager, MavenRunner runner) {
    myProject = project;
    myProjectsManager = projectsManager;
    myRunner = runner;
  }

  @Override
  public void initComponent() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    StartupManager.getInstance(myProject).runWhenProjectIsInitialized(new Runnable() {
      public void run() {
        doInit();
      }
    });
  }

  @TestOnly
  public void doInit() {
    myProjectsManager.addListener(new MyProjectStateListener());
    myKeymapListener = new MyKeymapListener();

    CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    compilerManager.addBeforeTask(new CompileTask() {
      public boolean execute(CompileContext context) {
        return MavenEventsManager.this.execute(getState().beforeCompile, context.getProgressIndicator());
      }
    });
    compilerManager.addAfterTask(new CompileTask() {
      public boolean execute(CompileContext context) {
        return MavenEventsManager.this.execute(getState().afterCompile, context.getProgressIndicator());
      }
    });
  }

  @Override
  public void disposeComponent() {
    Disposer.dispose(myKeymapUpdaterAlarm);

    if (myKeymapListener != null) {
      myKeymapListener.stopListen();
      myKeymapListener = null;
    }
  }

  public void addListener(Listener listener) {
    myListeners.add(listener);
  }

  public void removeListener(Listener listener) {
    myListeners.remove(listener);
  }

  @NotNull
  public MavenEventsState getState() {
    HashMap<String, MavenTask> map = new HashMap<String, MavenTask>();

    for (Map.Entry<Pair<String, Integer>, MavenTask> each : myBeforeRunMap.entrySet()) {
      Pair<String, Integer> key = each.getKey();
      String type = key.first;
      Integer configID = key.second;
      MavenTask task = each.getValue();

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

  public void loadState(MavenEventsState state) {
    HashMap<Pair<String, Integer>, MavenTask> map = new HashMap<Pair<String, Integer>, MavenTask>();

    for (Map.Entry<String, MavenTask> each : state.beforeRun.entrySet()) {
      String key = each.getKey();
      MavenTask task = each.getValue();
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

  public boolean execute(@NotNull Collection<MavenTask> mavenTasks, ProgressIndicator indicator) {
    final List<MavenRunnerParameters> parametersList = new ArrayList<MavenRunnerParameters>();
    for (MavenTask mavenTask : mavenTasks) {
      final MavenRunnerParameters runnerParameters = mavenTask.createBuildParameters(myProjectsManager);
      if (runnerParameters == null) {
        return false;
      }
      parametersList.add(runnerParameters);
    }
    return myRunner.runBatch(parametersList, null, null, EventsBundle.message("maven.event.executing"), indicator);
  }

  public String getActionId(@Nullable String pomPath, @Nullable String goal) {
    StringBuilder result = new StringBuilder(ACTION_ID_PREFIX);
    result.append(myProject.getLocationHash());

    if (pomPath != null) {
      String portablePath = FileUtil.toSystemIndependentName(pomPath);

      result.append(new File(portablePath).getParentFile().getName());
      result.append(Integer.toHexString(portablePath.hashCode()));

      if (goal != null) result.append(goal);
    }

    return result.toString();
  }

  public String getActionDescription(@NotNull String pomPath, @NotNull final String goal) {
    final String actionId = getActionId(pomPath, goal);
    if (actionId == null) {
      return null;
    }

    final String shortcutString = getShortcutString(actionId);
    final MavenTask mavenTask = new MavenTask(pomPath, goal);

    final StringBuilder stringBuilder = new StringBuilder();
    appendIf(stringBuilder, shortcutString != null, shortcutString);
    appendIf(stringBuilder, myState.beforeCompile.contains(mavenTask), BEFORE_MAKE);
    appendIf(stringBuilder, myState.afterCompile.contains(mavenTask), AFTER_MAKE);
    appendIf(stringBuilder, hasAssginments(mavenTask), BEFORE_RUN);

    return stringBuilder.length() == 0 ? null : stringBuilder.toString();
  }

  private static String getShortcutString(String actionId) {
    Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
    Shortcut[] shortcuts = activeKeymap.getShortcuts(actionId);
    if (shortcuts != null && shortcuts.length > 0) {
      return KeymapUtil.getShortcutText(shortcuts[0]);
    }
    else {
      return null;
    }
  }

  private void appendIf(StringBuilder stringBuilder, boolean on, String text) {
    if (on) {
      if (stringBuilder.length() > 0) {
        stringBuilder.append(", ");
      }
      stringBuilder.append(text);
    }
  }

  public void updateShortcuts(@Nullable String actionId) {
    for (Listener listener : myListeners) {
      listener.updateShortcuts(actionId);
    }
  }

  public void updateTaskShortcuts(@NotNull MavenTask mavenTask) {
    updateShortcuts(getActionId(mavenTask.pomPath, mavenTask.goal));
  }

  public void installTaskSelector(TaskSelector selector) {
    myTaskSelector = selector;
  }

  @Nullable
  private MavenTask selectMavenTask(Project project, RunConfiguration runConfiguration) {
    if (myTaskSelector == null) {
      return null;
    }

    final MavenTask mavenTask = getTask(runConfiguration.getType(), runConfiguration);

    if (!myTaskSelector.select(project, mavenTask != null ? mavenTask.pomPath : null, mavenTask != null ? mavenTask.goal : null,
                               EventsBundle.message("maven.event.select.goal.title"))) {
      return mavenTask;
    }

    final String pomPath = myTaskSelector.getSelectedPomPath();
    if (pomPath != null) {
      final MavenTask newMavenTask = new MavenTask(pomPath, myTaskSelector.getSelectedGoal());
      if (!isAssignedForType(runConfiguration.getType(), newMavenTask)) {
        assignTask(runConfiguration.getType(), runConfiguration, newMavenTask);
        updateTaskShortcuts(newMavenTask);
      }
      return newMavenTask;
    }

    clearAssignment(runConfiguration.getType(), runConfiguration);
    updateShortcuts(null);
    return null;
  }

  public String configureRunStep(final RunConfiguration runConfiguration) {
    return getDescription(selectMavenTask(myProject, runConfiguration));
  }

  public String getRunStepDescription(final RunConfiguration runConfiguration) {
    return getDescription(getTask(runConfiguration.getType(), runConfiguration));
  }

  public MavenTask getAssignedTask(ConfigurationType type, RunConfiguration config) {
    return myBeforeRunMap.get(getKey(type, config));
  }

  MavenTask getTask(ConfigurationType type, RunConfiguration config) {
    final MavenTask task = getAssignedTask(type, config);
    return task != null ? task : getAssignedTask(type, null);
  }

  public boolean hasAssginments(MavenTask task) {
    return myBeforeRunMap.containsValue(task);
  }

  public boolean isAssignedForType(ConfigurationType type, MavenTask mavenTask) {
    return mavenTask.equals(getAssignedTask(type, null));
  }

  public void assignTask(ConfigurationType type, RunConfiguration config, MavenTask mavenTask) {
    myBeforeRunMap.put(getKey(type, config), mavenTask);
  }

  public void clearAssignment(ConfigurationType type, RunConfiguration config) {
    myBeforeRunMap.remove(getKey(type, config));
  }

  public void clearAssignments(MavenTask task) {
    final Collection<Pair<String, Integer>> keysToRemove = new ArrayList<Pair<String, Integer>>();
    final Collection<String> oldKeysToRemove = new ArrayList<String>();

    for (Map.Entry<Pair<String, Integer>, MavenTask> each : myBeforeRunMap.entrySet()) {
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

  static String getOldVersionKey(ConfigurationType type, RunConfiguration configuration) {
    if (configuration != null) {
      return type.getDisplayName() + "#" + configuration.getName();
    }
    else {
      return type.getDisplayName();
    }
  }

  @Nullable
  public static MavenTask getMavenTask(DataContext dataContext) {
    if (dataContext != null) {
      final VirtualFile virtualFile = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
      if (virtualFile != null && MavenConstants.POM_XML.equals(virtualFile.getName())) {
        final List<String> goals = MavenDataKeys.MAVEN_GOALS_KEY.getData(dataContext);
        if (goals != null && goals.size() == 1) {
          return new MavenTask(virtualFile.getPath(), goals.get(0));
        }
      }
    }
    return null;
  }

  @Nullable
  private String getDescription(MavenTask mavenTask) {
    if (mavenTask != null) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(mavenTask.pomPath);
      if (file != null) {
        MavenProjectModel project = myProjectsManager.findProject(file);
        if (project != null) {
          StringBuilder stringBuilder = new StringBuilder();
          stringBuilder.append("'");
          stringBuilder.append(project.getMavenId().artifactId);
          stringBuilder.append(" ");
          stringBuilder.append(mavenTask.goal);
          stringBuilder.append("'");
          return stringBuilder.toString();
        }
      }
    }
    return null;
  }

  private class MyKeymapListener implements KeymapManagerListener, Keymap.Listener {
    private Keymap myCurrentKeymap = null;

    public MyKeymapListener() {
      final KeymapManagerEx keymapManager = KeymapManagerEx.getInstanceEx();
      final Keymap activeKeymap = keymapManager.getActiveKeymap();
      listenTo(activeKeymap);
      keymapManager.addKeymapManagerListener(this);
    }

    public void activeKeymapChanged(Keymap keymap) {
      listenTo(keymap);
      updateShortcuts(null);
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
      updateShortcuts(actionId);
    }

    public void stopListen() {
      MavenKeymapExtension.clearActions(myProject);
      listenTo(null);
      KeymapManagerEx.getInstanceEx().removeKeymapManagerListener(this);
    }
  }

  private class MyProjectStateListener implements MavenProjectsManager.Listener {
    private volatile boolean isUpdateScheduled = false;

    public void activate() {
      scheduleKeymapUpdate();
    }

    public void projectAdded(MavenProjectModel project) {
      scheduleKeymapUpdate();
    }

    public void projectUpdated(MavenProjectModel project) {
      scheduleKeymapUpdate();
    }

    public void projectRemoved(MavenProjectModel project) {
      scheduleKeymapUpdate();
    }

    public void setIgnored(MavenProjectModel project, boolean on) {
      if (on) {
        projectRemoved(project);
      } else {
        projectAdded(project);
      }
    }

    public void profilesChanged(List<String> profiles) {
    }

    private void scheduleKeymapUpdate() {
      Runnable updateTask = new Runnable() {
        public void run() {
          if (myProject.isDisposed()) return;
          MavenKeymapExtension.createActions(myProject);
        }
      };

      if (ApplicationManager.getApplication().isUnitTestMode()) {
        updateTask.run();
      }
      else {
        myKeymapUpdaterAlarm.addRequest(updateTask, 10);
      }
    }
  }

  public static interface Listener {
    void updateShortcuts(@Nullable String actionId);
  }

  public static interface TaskSelector {
    boolean select(Project project, @Nullable String pomPath, @Nullable String goal, @NotNull String title);

    String getSelectedPomPath();

    String getSelectedGoal();
  }
}
