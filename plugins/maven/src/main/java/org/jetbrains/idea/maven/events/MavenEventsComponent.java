package org.jetbrains.idea.maven.events;

import com.intellij.execution.RunManager;
import com.intellij.execution.configurations.RunConfiguration;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.builder.MavenBuilder;
import org.jetbrains.idea.maven.builder.executor.MavenBuildParameters;
import org.jetbrains.idea.maven.core.util.DummyProjectComponent;
import org.jetbrains.idea.maven.core.util.MavenId;
import org.jetbrains.idea.maven.state.MavenProjectsState;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * @author Vladislav.Kaznacheev
 */
@State(name = "MavenEventsHandler", storages = {@Storage(id = "default", file = "$WORKSPACE_FILE$")})
public class MavenEventsComponent extends DummyProjectComponent implements PersistentStateComponent<MavenEventsState>, MavenEventsHandler {

  @NonNls private static final String ACTION_ID_PREFIX = "Maven_";

  public static final String RUN_MAVEN_STEP = EventsBundle.message("maven.event.before.run");

  private static final String BEFORE_MAKE = EventsBundle.message("maven.event.text.before.make");
  private static final String AFTER_MAKE = EventsBundle.message("maven.event.text.after.make");
  private static final String BEFORE_RUN = EventsBundle.message("maven.event.text.before.run");

  private final Project myProject;
  private final MavenProjectsState myProjectsState;
  private final MavenBuilder myMavenBuilder;

  private MavenEventsState myState = new MavenEventsState();

  private MyKeymapListener myKeymapListener;
  private Collection<Listener> myListeners = new HashSet<Listener>();
  private TaskSelector myTaskSelector;

  public MavenEventsComponent(final Project project, final MavenProjectsState projectsState, final MavenBuilder mavenBuilder) {
    myProject = project;
    myProjectsState = projectsState;
    myMavenBuilder = mavenBuilder;

    myProjectsState.addListener(new MyProjectStateListener(project));
  }

  public void addListener(Listener listener) {
    myListeners.add(listener);
  }

  public void removeListener(Listener listener) {
    myListeners.remove(listener);
  }

  @NotNull
  public MavenEventsState getState() {
    return myState;
  }

  public void loadState(MavenEventsState state) {
    myState = state;
  }

  public boolean execute(@NotNull Collection<MavenTask> mavenTasks) {
    final List<MavenBuildParameters> parametersList = new ArrayList<MavenBuildParameters>();
    for (MavenTask mavenTask : mavenTasks) {
      final MavenBuildParameters buildParameters = mavenTask.createBuildParameters(myProjectsState);
      if (buildParameters == null) {
        return false;
      }
      parametersList.add(buildParameters);
    }
    return myMavenBuilder.runBatch(parametersList, null, null, EventsBundle.message("maven.event.executing"));
  }

  public String getActionId(@Nullable String pomPath, @Nullable String goal) {
    final StringBuilder stringBuilder = new StringBuilder(ACTION_ID_PREFIX);
    stringBuilder.append(myProject.getLocationHash());
    if (pomPath != null) {
      final String portablePath = FileUtil.toSystemIndependentName(pomPath);
      stringBuilder.append(new File(portablePath).getParentFile().getName()).append(Integer.toHexString(portablePath.hashCode()));
      if (goal != null) {
        stringBuilder.append(goal);
      }
    }
    return stringBuilder.toString();
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
    appendIf(stringBuilder, myState.hasAssginments(mavenTask), BEFORE_RUN);

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

    final MavenTask mavenTask = myState.getTask(runConfiguration.getType(), runConfiguration.getName());

    if (!myTaskSelector.select(project, mavenTask != null ? mavenTask.pomPath : null, mavenTask != null ? mavenTask.goal : null,
                               EventsBundle.message("maven.event.select.goal.title"))) {
      return mavenTask;
    }

    final String pomPath = myTaskSelector.getSelectedPomPath();
    if (pomPath != null) {
      final MavenTask newMavenTask = new MavenTask(pomPath, myTaskSelector.getSelectedGoal());
      if (!myState.isAssignedForType(runConfiguration.getType(), newMavenTask)) {
        myState.assignTask(runConfiguration.getType(), runConfiguration.getName(), newMavenTask);
        updateTaskShortcuts(newMavenTask);
      }
      return newMavenTask;
    }

    myState.clearAssignment(runConfiguration.getType(), runConfiguration.getName());
    updateShortcuts(null);
    return null;
  }

  public void projectClosed() {
    if (myKeymapListener != null) {
      myKeymapListener.stopListen();
      myKeymapListener = null;
    }
  }

  private void subscribe() {
    RunManager.getInstance(myProject).registerStepBeforeRun(RUN_MAVEN_STEP, new Function<RunConfiguration, String>() {
      public String fun(RunConfiguration runConfiguration) {
        //noinspection ConstantConditions
        return getDescription(selectMavenTask(myProject, runConfiguration));
      }
    }, new Function<RunConfiguration, String>() {
      public String fun(RunConfiguration runConfiguration) {
        //noinspection ConstantConditions
        return getDescription(myState.getTask(runConfiguration.getType(), runConfiguration.getName()));
      }
    });

    CompilerManager compilerManager = CompilerManager.getInstance(myProject);
    compilerManager.addBeforeTask(new CompileTask() {
      public boolean execute(CompileContext context) {
        return MavenEventsComponent.this.execute(getState().beforeCompile);
      }
    });
    compilerManager.addAfterTask(new CompileTask() {
      public boolean execute(CompileContext context) {
        return MavenEventsComponent.this.execute(getState().afterCompile);
      }
    });

    myKeymapListener = new MyKeymapListener();
  }

  @Nullable
  private String getDescription(MavenTask mavenTask) {
    if (mavenTask != null) {
      VirtualFile file = LocalFileSystem.getInstance().findFileByPath(mavenTask.pomPath);
      if (file != null) {
        final MavenProject mavenProject = myProjectsState.getMavenProject(file);
        if (mavenProject != null) {
          StringBuilder stringBuilder = new StringBuilder();
          stringBuilder.append("'");
          stringBuilder.append(mavenProject.getArtifactId());
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

  private class MyProjectStateListener implements MavenProjectsState.Listener {

    boolean updateScheduled;
    private final Project myProject;

    public MyProjectStateListener(final Project project) {
      myProject = project;
      updateScheduled = false;
    }

    public void activate() {
      subscribe();
      requestKeymapUpdate();
    }

    public void createFile(VirtualFile file) {
      requestKeymapUpdate();
    }

    public void removeFile(VirtualFile file) {
      requestKeymapUpdate();
    }

    public void updateFile(VirtualFile file) {
      requestKeymapUpdate();
    }

    public void setIgnored(VirtualFile file, boolean on) {
      requestKeymapUpdate();
    }

    public void setProfiles(VirtualFile file, @NotNull Collection<String> profiles) {
      requestKeymapUpdate();
    }

    public void attachPlugins(final VirtualFile file, @NotNull final Collection<MavenId> plugins) {
      requestKeymapUpdate();
    }

    public void detachPlugins(final VirtualFile file, @NotNull final Collection<MavenId> plugins) {
      requestKeymapUpdate();
    }

    private void requestKeymapUpdate() {
      if (!updateScheduled) {
        updateScheduled = true;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (myProject.isOpen()) {
              MavenKeymapExtension.createActions(myProject);
            }
            updateScheduled = false;
          }
        });
      }
    }
  }
}
