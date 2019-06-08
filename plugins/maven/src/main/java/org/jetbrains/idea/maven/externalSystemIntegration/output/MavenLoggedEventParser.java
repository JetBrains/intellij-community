// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.build.events.BuildEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

@ApiStatus.Experimental
public interface MavenLoggedEventParser {

  boolean supportsType(@Nullable LogMessageType type);

  boolean checkLogLine(@NotNull Object parentId,
                       @NotNull MavenLogEntryReader.MavenLogEntry logLine,
                       @NotNull MavenLogEntryReader logEntryReader,
                       @NotNull Consumer<? super BuildEvent> messageConsumer);


  default void finish(@NotNull ExternalSystemTaskId taskId, @NotNull Consumer<? super BuildEvent> messageConsumer) {}
}
