// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.tasks;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.idea.maven.execution.MavenRunner;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectChanges;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.MavenProjectsTree;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.MavenMergingUpdateQueue;
import org.jetbrains.idea.maven.utils.MavenSimpleProjectComponent;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class MavenShortcutsManager extends MavenSimpleProjectComponent implements Disposable, BaseComponent {
  private static final String ACTION_ID_PREFIX = "Maven_";

  private final AtomicBoolean isInitialized = new AtomicBoolean();

  private final MavenProjectsManager myProjectsManager;

  private final List<Listener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  @NotNull
  public static MavenShortcutsManager getInstance(Project project) {
    return project.getComponent(MavenShortcutsManager.class);
  }

  public MavenShortcutsManager(Project project, MavenProjectsManager projectsManager, MavenRunner runner) {
    super(project);
    myProjectsManager = projectsManager;
  }

  @Override
  public void dispose() {

  }

  @Override
  public void initComponent() {
    if (!isNormalProject()) return;

    MavenUtil.runWhenInitialized(myProject, (DumbAwareRunnable)() -> doInit());
  }

  @TestOnly
  public void doInit() {
    if (isInitialized.getAndSet(true)) return;

    MyProjectsTreeListener listener = new MyProjectsTreeListener();
    myProjectsManager.addManagerListener(listener);
    myProjectsManager.addProjectsTreeListener(listener);

    ApplicationManager.getApplication().getMessageBus().connect(this).subscribe(KeymapManagerListener.TOPIC, new KeymapManagerListener() {
      {
        ApplicationManager.getApplication().getMessageBus().connect(MavenShortcutsManager.this).subscribe(KeymapManagerListener.TOPIC, this);
      }

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
  public void disposeComponent() {
    if (!isInitialized.getAndSet(false)) return;

    MavenKeymapExtension.clearActions(myProject);
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

  public String getDescription(MavenProject project, String goal) {
    Shortcut[] shortcuts = getShortcuts(project, goal);
    if (shortcuts.length == 0) return "";
    return KeymapUtil.getShortcutsText(shortcuts);
  }

  public boolean hasShortcuts(MavenProject project, String goal) {
    return getShortcuts(project, goal).length > 0;
  }

  @NotNull
  private Shortcut[] getShortcuts(MavenProject project, String goal) {
    String actionId = getActionId(project.getPath(), goal);
    if (actionId == null) return Shortcut.EMPTY_ARRAY;
    Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
    return activeKeymap.getShortcuts(actionId);
  }

  private void fireShortcutsUpdated() {
    for (Listener listener : myListeners) {
      listener.shortcutsUpdated();
    }
  }

  public void addListener(Listener listener) {
    myListeners.add(listener);
  }

  public interface Listener {
    void shortcutsUpdated();
  }

  private class MyProjectsTreeListener implements MavenProjectsManager.Listener, MavenProjectsTree.Listener {
    private final Map<MavenProject, Boolean> mySheduledProjects = new THashMap<>();
    private final MergingUpdateQueue myUpdateQueue = new MavenMergingUpdateQueue("MavenShortcutsManager: Keymap Update",
                                                                                 500, true, myProject);

    @Override
    public void activated() {
      scheduleKeymapUpdate(myProjectsManager.getNonIgnoredProjects(), true);
    }

    @Override
    public void projectsScheduled() {
    }

    @Override
    public void importAndResolveScheduled() {
    }

    @Override
    public void projectsIgnoredStateChanged(List<MavenProject> ignored, List<MavenProject> unignored, boolean fromImport) {
      scheduleKeymapUpdate(unignored, true);
      scheduleKeymapUpdate(ignored, false);
    }

    @Override
    public void projectsUpdated(List<Pair<MavenProject, MavenProjectChanges>> updated, List<MavenProject> deleted) {
      scheduleKeymapUpdate(MavenUtil.collectFirsts(updated), true);
      scheduleKeymapUpdate(deleted, false);
    }

    @Override
    public void projectResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                NativeMavenProjectHolder nativeMavenProject) {
      scheduleKeymapUpdate(Collections.singletonList(projectWithChanges.first), true);
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
