// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.build.events.BuildEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public interface MavenLoggedEventParser {
  String COMPILER_MESSAGES_GROUP = "Java compiler";

  boolean supportsType(@Nullable LogMessageType type);

  boolean checkLogLine(@NotNull ExternalSystemTaskId id,
                       @NotNull String line,
                       @Nullable LogMessageType type,
                       @NotNull Consumer<? super BuildEvent> messageConsumer);

  default void finish(@NotNull ExternalSystemTaskId taskId, @NotNull Consumer<? super BuildEvent> messageConsumer) {}
}
