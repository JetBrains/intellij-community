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
package org.jetbrains.idea.maven.project.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.idea.maven.model.MavenArtifact;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.MavenDataKeys;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DownloadSelectedSourcesAndDocsAction extends MavenProjectsAction {
  private boolean mySources;
  private boolean myDocs;

  @SuppressWarnings({"UnusedDeclaration"})
  public DownloadSelectedSourcesAndDocsAction() {
    this(true, true);
  }

  public DownloadSelectedSourcesAndDocsAction(boolean sources, boolean docs) {
    mySources = sources;
    myDocs = docs;
  }

  @Override
  protected boolean isAvailable(AnActionEvent e) {
    return super.isAvailable(e) && !getDependencies(e).isEmpty();
  }

  private Collection<MavenArtifact> getDependencies(AnActionEvent e) {
    Collection<MavenArtifact> result = e.getData(MavenDataKeys.MAVEN_DEPENDENCIES);
    return result == null ? Collections.<MavenArtifact>emptyList() : result;
  }

  protected void perform(MavenProjectsManager manager, List<MavenProject> mavenProjects, AnActionEvent e) {
    manager.scheduleArtifactsDownloading(mavenProjects, getDependencies(e), mySources, myDocs, null);
  }
}