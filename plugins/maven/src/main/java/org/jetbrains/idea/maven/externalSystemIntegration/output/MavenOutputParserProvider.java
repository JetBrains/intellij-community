// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.build.output.BuildOutputParser;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemOutputParserProvider;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.idea.maven.externalSystemIntegration.output.events.*;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.List;

@ApiStatus.Experimental
public class MavenOutputParserProvider implements ExternalSystemOutputParserProvider {
  @Override
  public ProjectSystemId getExternalSystemId() {
    return MavenUtil.SYSTEM_ID;
  }

  @Override
  public List<BuildOutputParser> getBuildOutputParsers(ExternalSystemTask task) {
    return ContainerUtil.list(createMavenOutputParser(task.getId()));
  }

  public static MavenLogOutputParser createMavenOutputParser(ExternalSystemTaskId taskId) {
    return new MavenLogOutputParser(taskId,
                                    ContainerUtil.list(
                                      new ArtifactDownloadScanning(),
                                      new JavaBuildErrorNotification(),
                                      new KotlinBuildErrorNotification(),
                                      new ProjectScanning(),
                                      new WarningNotifier(),
                                      new CommonErrorParser()
                                    ));
  }
}
