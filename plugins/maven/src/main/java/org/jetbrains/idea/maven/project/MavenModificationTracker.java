/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.facade.NativeMavenProjectHolder;

import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class MavenModificationTracker implements ModificationTracker, MavenProjectsTree.Listener {
  private long myCounter = 0;
  public MavenModificationTracker(MavenProjectsManager manager) {
    manager.addProjectsTreeListener(this);
  }

  private void inc() {
    myCounter++;
  }

  @Override
  public long getModificationCount() {
    return myCounter;
  }

  @Override
  public void profilesChanged() {
    inc();
  }

  @Override
  public void projectsIgnoredStateChanged(List<MavenProject> ignored, List<MavenProject> unignored, Object message) {
    inc();
  }

  @Override
  public void projectsUpdated(List<Pair<MavenProject, MavenProjectChanges>> updated, List<MavenProject> deleted, Object message) {
    inc();
  }

  @Override
  public void projectResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                              @Nullable NativeMavenProjectHolder nativeMavenProject,
                              Object message) {
    inc();
  }

  @Override
  public void pluginsResolved(MavenProject project) {
    inc();
  }

  @Override
  public void foldersResolved(Pair<MavenProject, MavenProjectChanges> projectWithChanges, Object message) {
    inc();
  }

  @Override
  public void artifactsDownloaded(MavenProject project) {
    inc();
  }
}
