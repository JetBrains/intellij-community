// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven;

import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.idea.maven.importing.MavenImporter;
import org.jetbrains.idea.maven.importing.MavenRootModelAdapter;
import org.jetbrains.idea.maven.project.*;
import org.jetbrains.idea.maven.utils.MavenProcessCanceledException;
import org.jetbrains.idea.maven.utils.MavenProgressIndicator;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class TestCompletionMavenImporter extends MavenImporter {
  private final AtomicInteger myImported = new AtomicInteger(0);
  public TestCompletionMavenImporter() {
    super("", "");
  }


  public boolean isApplicable(MavenProject mavenProject) {
    return true;
  }

  @Override
  public void preProcess(Module module,
                         MavenProject mavenProject,
                         MavenProjectChanges changes,
                         IdeModifiableModelsProvider modifiableModelsProvider) {

  }

  @Override
  public void process(IdeModifiableModelsProvider modifiableModelsProvider,
                      Module module,
                      MavenRootModelAdapter rootModel,
                      MavenProjectsTree mavenModel,
                      MavenProject mavenProject,
                      MavenProjectChanges changes,
                      Map<MavenProject, String> mavenProjectToModuleName,
                      List<MavenProjectsProcessorTask> postTasks) {

    postTasks.add(new MavenProjectsProcessorTask() {
      @Override
      public void perform(Project project,
                          MavenEmbeddersManager embeddersManager,
                          MavenConsole console, MavenProgressIndicator indicator) throws MavenProcessCanceledException {

        myImported.incrementAndGet();
      }
    });

  }

  public boolean isImported() {
    return myImported.get() > 1;
  }

  public void reset() {
    myImported.set(0);
  }
}
