/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project.importing;

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenExplicitProfiles;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.server.NativeMavenProjectHolder;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

public abstract class MavenProjectsTreeTestCase extends MavenMultiVersionImportingTestCase {
  protected MavenProjectsTree myTree;


  @Override
  protected void setUpInWriteAction() throws Exception {
    super.setUpInWriteAction();
    myTree = MavenProjectsManager.getInstance(myProject).getProjectsTree();
  }

  protected void updateAll(VirtualFile... files) {
    updateAll(Collections.emptyList(), files);
  }

  protected void updateAll(List<String> profiles, VirtualFile... files) {
    myTree.resetManagedFilesAndProfiles(asList(files), new MavenExplicitProfiles(profiles));
    myTree.updateAll(false, getMavenGeneralSettings(), getMavenProgressIndicator().getIndicator());
  }

  protected void update(VirtualFile file) {
    myTree.update(asList(file), false, getMavenGeneralSettings(), getMavenProgressIndicator().getIndicator());
  }

  protected void deleteProject(VirtualFile file) {
    myTree.delete(asList(file), getMavenGeneralSettings(), getMavenProgressIndicator().getIndicator());
  }

  protected void updateTimestamps(final VirtualFile... files) throws IOException {
    WriteAction.runAndWait(() -> {
      for (VirtualFile each : files) {
        each.setBinaryContent(each.contentsToByteArray());
      }
    });
  }

  protected static ListenerLog log() {
    return new ListenerLog();
  }

  protected static class ListenerLog extends CopyOnWriteArrayList<Pair<String, Set<String>>> {
    ListenerLog() { super(); }

    ListenerLog(ListenerLog log) { super(log); }

    ListenerLog add(String key, String... values) {
      var log = new ListenerLog(this);
      log.add(new Pair<>(key, Set.of(values)));
      return log;
    }
  }

  protected static class MyLoggingListener implements MavenProjectsTree.Listener {
    List<Pair<String, Set<String>>> log = new CopyOnWriteArrayList<>();

    private void add(String key, Set<String> value) {
      log.add(new Pair<>(key, value));
    }

    @Override
    public void projectsUpdated(@NotNull List<Pair<MavenProject, MavenProjectChanges>> updated, @NotNull List<MavenProject> deleted) {
      append(MavenUtil.collectFirsts(updated), "updated");
      append(deleted, "deleted");
    }

    private void append(List<MavenProject> updated, String text) {
      add(text, updated.stream().map(each -> each.getMavenId().getArtifactId()).collect(Collectors.toSet()));
    }

    @Override
    public void projectResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges,
                                NativeMavenProjectHolder nativeMavenProject) {
      add("resolved", Set.of(projectWithChanges.first.getMavenId().getArtifactId()));
    }

    @Override
    public void pluginsResolved(@NotNull MavenProject project) {
      add("plugins", Set.of(project.getMavenId().getArtifactId()));
    }

    @Override
    public void foldersResolved(@NotNull Pair<MavenProject, MavenProjectChanges> projectWithChanges) {
      add("folders", Set.of(projectWithChanges.first.getMavenId().getArtifactId()));
    }
  }

  protected void resolve(@NotNull Project project,
                         @NotNull MavenProject mavenProject,
                         @NotNull MavenGeneralSettings generalSettings,
                         @NotNull MavenEmbeddersManager embeddersManager,
                         @NotNull MavenConsole console,
                         @NotNull MavenProgressIndicator process) throws MavenProcessCanceledException {
    var resolver = MavenProjectResolver.getInstance(project);
    resolver.resolve(List.of(mavenProject),
                     myTree,
                     generalSettings,
                     embeddersManager,
                     console,
                     process.getIndicator(),
                     process.getSyncConsole());
  }
}
