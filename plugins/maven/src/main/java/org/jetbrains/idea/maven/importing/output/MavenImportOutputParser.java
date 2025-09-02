// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.importing.output;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.output.BuildOutputInstantReader;
import com.intellij.build.output.BuildOutputParser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.importproject.MavenImportLoggedEventParser;

import java.util.function.Consumer;

public class MavenImportOutputParser implements BuildOutputParser {

  private final Project myProject;

  public MavenImportOutputParser(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public boolean parse(@NotNull String line,
                       @Nullable BuildOutputInstantReader reader,
                       @NotNull Consumer<? super BuildEvent> messageConsumer) {
    if (StringUtil.isEmptyOrSpaces(line)) {
      return false;
    }

    for (MavenImportLoggedEventParser event : MavenImportLoggedEventParser.EP_NAME.getExtensionList()) {
      if (event.processLogLine(myProject, line, reader, messageConsumer)) {
        return true;
      }
    }

    return false;
  }
}
