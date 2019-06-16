// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.build.output.BuildOutputParser;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemOutputParserProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.externalSystemIntegration.output.parsers.*;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.Arrays;
import java.util.List;

@ApiStatus.Experimental
public class MavenOutputParserProvider implements ExternalSystemOutputParserProvider {
  @Override
  public ProjectSystemId getExternalSystemId() {
    return MavenUtil.SYSTEM_ID;
  }

  @Override
  public List<BuildOutputParser> getBuildOutputParsers(@NotNull ExternalSystemTaskId taskId) {
    throw new UnsupportedOperationException();
  }

  public static MavenLogOutputParser createMavenOutputParser(ExternalSystemTaskId taskId) {
    return new MavenLogOutputParser(taskId,
                                    Arrays.asList( new JavaBuildErrorNotification(),
                                                   new KotlinBuildErrorNotification(),
                                                   new WarningNotifier(),
                                                   new ErrorNotifier(),
                                                   new MavenBadConfigEventParser()));
  }
}
