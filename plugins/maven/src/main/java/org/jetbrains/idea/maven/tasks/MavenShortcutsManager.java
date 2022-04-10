// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.tasks;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.DisposableWrapperList;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.MavenMergingUpdateQueue;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MavenShortcutsManager implements Disposable {
  private final Project myProject;

  private static final String ACTION_ID_PREFIX = "Maven_";

  private final AtomicBoolean isInitialized = new AtomicBoolean();

  private final DisposableWrapperList<Listener> myListeners = new DisposableWrapperList<>();

  @NotNull
  public static MavenShortcutsManager getInstance(Project project) {
    return project.getService(MavenShortcutsManager.class);
  }

  @Nullable
  public static MavenShortcutsManager getInstanceIfCreated(@NotNull Project project) {
    return project.getServiceIfCreated(MavenShortcutsManager.class);
  }

  public MavenShortcutsManager(@NotNull Project project) {
    myProject = project;

    if (MavenUtil.isMavenUnitTestModeEnabled() || ApplicationManager.getApplication().isHeadlessEnvironment()) {
      return;
    }

    MavenUtil.runWhenInitialized(myProject, (DumbAwareRunnable)() -> doInit(project));
  }

  @TestOnly
  public void doInit(@NotNull Project project) {
    if (isInitialized.getAndSet(true)) return;

    MyProjectsTreeListener listener = new MyProjectsTreeListener();
    MavenProjectsManager mavenProjectManager = MavenProjectsManager.getInstance(project);
    mavenProjectManager.addManagerListener(listener);
    mavenProjectManager.addProjectsTreeListener(listener, this);

    MessageBusConnection busConnection = ApplicationManager.getApplication().getMessageBus().connect(this);
    busConnection.subscribe(KeymapManagerListener.TOPIC, new KeymapManagerListener() {
      @Override
      public void activeKeymapChanged(Keymap keymap) {
        fireShortcutsUpdated();
      }

      @Override
      public void shortcutChanged(@NotNull Keymap keymap, @NotNull String actionId) {
        fireShortcutsUpdated();
      }
    });
  }

  @Override
  public void dispose() {
    if (!isInitialized.getAndSet(false)) {
      return;
    }

    MavenKeymapExtension.clearActions(myProject);
    myListeners.clear();
  }

  @NotNull
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

  public String getDescription(MavenProject project, String goal) {
    Shortcut[] shortcuts = getShortcuts(project, goal);
    if (shortcuts.length == 0) return "";
    return KeymapUtil.getShortcutsText(shortcuts);
  }

  boolean hasShortcuts(MavenProject project, String goal) {
    return getShortcuts(project, goal).length > 0;
  }

  private Shortcut @NotNull [] getShortcuts(MavenProject project, String goal) {
    String actionId = getActionId(project.getPath(), goal);
    Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
    return activeKeymap.getShortcuts(actionId);
  }

  private void fireShortcutsUpdated() {
    for (Listener listener : myListeners) {
      listener.shortcutsUpdated();
    }
  }

  public void addListener(@NotNull Listener l, @NotNull Disposable disposable) {
    myListeners.add(l, disposable);
  }

  @FunctionalInterface
  public interface Listener {
    void shortcutsUpdated();
  }

  private class MyProjectsTreeListener implements MavenProjectsManager.Listener, MavenProjectsTree.Listener {
    private final Map<MavenProject, Boolean> mySheduledProjects = new HashMap<>();
    private final MergingUpdateQueue myUpdateQueue = new MavenMergingUpdateQueue("MavenShortcutsManager: Keymap Update",
                                                                                 500, true, MavenShortcutsManager.this).usePassThroughInUnitTestMode();

    @Override
    public void activated() {
      scheduleKeymapUpdate(MavenProjectsManager.getInstance(myProject).getNonIgnoredProjects(), true);
    }

    @Override
    public void projectsIgnoredStateChanged(@NotNull List<MavenProject> ignored, @NotNull List<MavenProject> unignored, boolean fromImport) {
      scheduleKeymapUpdate(unignored, true);
      scheduleKeymapUpdate(ignored, false);
    }

    @Override
    public void projectsUpdated(@NotNull List<Pair<MavenProject, MavenProjectChanges>> updated, @NotNull List<MavenProject> deleted) {
      scheduleKeymapUpdate(MavenUtil.collectFirsts(updated), true);
      scheduleKeymapUpdate(deleted, false);
    }

    @Override
    public void projectResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                NativeMavenProjectHolder nativeMavenProject) {
      scheduleKeymapUpdate(Collections.singletonList(projectWithChanges.first), true);
    }

    @Override
    public void pluginsResolved(@NotNull MavenProject project) {
      scheduleKeymapUpdate(Collections.singletonList(project), true);
    }

    private void scheduleKeymapUpdate(List<? extends MavenProject> mavenProjects, boolean forUpdate) {
      synchronized (mySheduledProjects) {
        for (MavenProject each : mavenProjects) {
          mySheduledProjects.put(each, forUpdate);
        }
      }

      myUpdateQueue.queue(new Update(MavenShortcutsManager.this) {
        @Override
        public void run() {
          List<MavenProject> projectToUpdate;
          List<MavenProject> projectToDelete;
          synchronized (mySheduledProjects) {
            projectToUpdate = selectScheduledProjects(true);
            projectToDelete = selectScheduledProjects(false);
            mySheduledProjects.clear();
          }

          if (!myProject.isDisposed()) {
            MavenKeymapExtension.clearActions(myProject, projectToDelete);
            MavenKeymapExtension.updateActions(myProject, projectToUpdate);
          }
        }
      });
    }

    private List<MavenProject> selectScheduledProjects(final boolean forUpdate) {
      return ContainerUtil.mapNotNull(mySheduledProjects.entrySet(),
                                      eachEntry -> forUpdate == eachEntry.getValue() ? eachEntry.getKey() : null);
    }
  }
}
