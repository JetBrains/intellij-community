// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing.output;

import com.intellij.build.output.BuildOutputParser;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemOutputParserProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class MavenImportOutputParserProvider implements ExternalSystemOutputParserProvider {
  @Override
  public ProjectSystemId getExternalSystemId() {
    return null;
  }

  @Override
  public List<BuildOutputParser> getBuildOutputParsers(@NotNull ExternalSystemTaskId taskId) {
    return Collections.singletonList(new MavenImportOutputParser());
  }
}
