// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.build.output.BuildOutputParser;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemOutputParserProvider;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.execution.MavenRunConfiguration;
import org.jetbrains.idea.maven.utils.MavenUtil;

import java.util.List;
import java.util.function.Function;

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

  public static MavenLogOutputParser createMavenOutputParser(@NotNull MavenRunConfiguration runConfiguration,
                                                             @NotNull ExternalSystemTaskId taskId,
                                                             @NotNull Function<String, String> targetFileMapper) {
    return new MavenLogOutputParser(runConfiguration,
                                    taskId,
                                    targetFileMapper,
                                    MavenLoggedEventParser.EP_NAME.getExtensionList());
  }
}
