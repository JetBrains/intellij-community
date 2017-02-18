/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.tasks;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.DumbAwareRunnable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.Function;
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

public class MavenShortcutsManager extends MavenSimpleProjectComponent implements Disposable {
  private static final String ACTION_ID_PREFIX = "Maven_";

  private final AtomicBoolean isInitialized = new AtomicBoolean();

  private final MavenProjectsManager myProjectsManager;

  private MyKeymapListener myKeymapListener;
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

    MavenUtil.runWhenInitialized(myProject, new DumbAwareRunnable() {
      @Override
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
  }

  @Override
  public void disposeComponent() {
    if (!isInitialized.getAndSet(false)) return;

    myKeymapListener.stopListen();
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

  private class MyKeymapListener implements KeymapManagerListener, Keymap.Listener {
    private Keymap myCurrentKeymap = null;

    public MyKeymapListener() {
      KeymapManager keymapManager = KeymapManager.getInstance();
      listenTo(keymapManager.getActiveKeymap());
      keymapManager.addKeymapManagerListener(this, MavenShortcutsManager.this);
    }

    @Override
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

    @Override
    public void onShortcutChanged(String actionId) {
      fireShortcutsUpdated();
    }

    public void stopListen() {
      listenTo(null);
    }
  }

  private class MyProjectsTreeListener extends MavenProjectsTree.ListenerAdapter implements MavenProjectsManager.Listener {
    private final Map<MavenProject, Boolean> mySheduledProjects = new THashMap<>();
    private final MergingUpdateQueue myUpdateQueue = new MavenMergingUpdateQueue(getComponentName() + ": Keymap Update",
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
