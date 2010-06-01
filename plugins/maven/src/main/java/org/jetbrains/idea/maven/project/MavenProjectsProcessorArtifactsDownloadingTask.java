/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.EmptyRunnable;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Collection;

public class MavenProjectsProcessorArtifactsDownloadingTask implements MavenProjectsProcessorTask {
  private final Collection<MavenProject> myProjects;
  private final Collection<MavenArtifact> myArtifacts;
  private final MavenProjectsTree myTree;
  private final boolean myDownloadSources;
  private final boolean myDownloadDocs;
  private final AsyncResult<MavenArtifactDownloader.DownloadResult> myCallbackResult;

  public MavenProjectsProcessorArtifactsDownloadingTask(Collection<MavenProject> projects,
                                                        Collection<MavenArtifact> artifacts,
                                                        MavenProjectsTree tree,
                                                        boolean downloadSources,
                                                        boolean downloadDocs,
                                                        AsyncResult<MavenArtifactDownloader.DownloadResult> callbackResult) {
    myProjects = projects;
    myArtifacts = artifacts;
    myTree = tree;
    myDownloadSources = downloadSources;
    myDownloadDocs = downloadDocs;
    myCallbackResult = callbackResult;
  }

  public void perform(final Project project, MavenEmbeddersManager embeddersManager, MavenConsole console, MavenProgressIndicator indicator)
    throws MavenProcessCanceledException {
    MavenArtifactDownloader.DownloadResult result =
      myTree.downloadArtifacts(myProjects, myArtifacts, myDownloadSources, myDownloadDocs, embeddersManager, console, indicator);
    if (myCallbackResult != null) myCallbackResult.setDone(result);

    // todo: hack to update all file pointers.
    MavenUtil.invokeLater(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), false, true);
          }
        });
      }
    });
  }
}