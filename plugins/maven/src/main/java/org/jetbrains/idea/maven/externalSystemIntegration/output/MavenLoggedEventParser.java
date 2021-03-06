// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.build.events.BuildEvent;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

@ApiStatus.Experimental
public interface MavenLoggedEventParser {
  ExtensionPointName<MavenLoggedEventParser> EP_NAME = ExtensionPointName.create("org.jetbrains.idea.maven.log.parser");

  boolean supportsType(@Nullable LogMessageType type);

  boolean checkLogLine(@NotNull Object parentId,
                       @NotNull MavenParsingContext parsingContext,
                       @NotNull MavenLogEntryReader.MavenLogEntry logLine,
                       @NotNull MavenLogEntryReader logEntryReader,
                       @NotNull Consumer<? super BuildEvent> messageConsumer);


  default void finish(@NotNull ExternalSystemTaskId taskId, @NotNull Consumer<? super BuildEvent> messageConsumer) {}
}
