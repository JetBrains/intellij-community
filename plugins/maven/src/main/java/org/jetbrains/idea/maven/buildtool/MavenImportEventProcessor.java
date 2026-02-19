// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.buildtool;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.BuildIssueEvent;
import com.intellij.build.output.BuildOutputInstantReaderImpl;
import com.intellij.execution.process.AnsiEscapeDecoder;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.importing.output.MavenImportOutputParser;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.util.Collections;

@ApiStatus.Experimental
public class MavenImportEventProcessor implements AnsiEscapeDecoder.ColoredTextAcceptor {
  private final @NotNull BuildOutputInstantReaderImpl myInstantReader;
  private final @NotNull MavenProjectsManager myProjectsManager;

  public MavenImportEventProcessor(@NotNull Project project) {
    myProjectsManager = MavenProjectsManager.getInstance(project);

    ExternalSystemTaskId taskId = myProjectsManager.getSyncConsole().getTaskId();
    myInstantReader = new BuildOutputInstantReaderImpl(
      taskId, taskId,
      (Object buildId, BuildEvent event) -> {
        if (event instanceof BuildIssueEvent) {
          myProjectsManager.getSyncConsole().addBuildIssue(((BuildIssueEvent)event).getIssue(), ((BuildIssueEvent)event).getKind());
        }
      },
      Collections.singletonList(new MavenImportOutputParser(project))
    );
  }

  public void finish() {
    myInstantReader.close();
  }

  @Override
  public void coloredTextAvailable(@NotNull String text, @NotNull Key outputType) {
    myInstantReader.append(text);
  }
}