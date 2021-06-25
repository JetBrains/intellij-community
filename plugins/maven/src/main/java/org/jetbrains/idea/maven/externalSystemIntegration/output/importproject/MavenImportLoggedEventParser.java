// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.importproject;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.output.BuildOutputInstantReader;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

@ApiStatus.Experimental
public interface MavenImportLoggedEventParser {
  ExtensionPointName<MavenImportLoggedEventParser> EP_NAME = ExtensionPointName.create("org.jetbrains.idea.maven.log.import.parser");

  /**
   * processing log line from vm process - maven server.
   * @param project - project
   * @param logLine - log line
   * @param reader - log reader
   * @param messageConsumer - message consumer (MavenSyncConsole)
   * @return true if log line consumed by messageConsumer
   */
  boolean processLogLine(
    @NotNull Project project,
    @NotNull String logLine,
    @NotNull BuildOutputInstantReader reader,
    @NotNull Consumer<? super BuildEvent> messageConsumer);

}

