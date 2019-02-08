// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.events;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.MessageEventImpl;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser;

import java.util.function.Consumer;

public class OOMEventParser implements MavenLoggedEventParser {
  @Override
  public boolean supportsType(@Nullable LogMessageType type) {
    return true;
  }

  @Override
  public boolean checkLogLine(@NotNull ExternalSystemTaskId id,
                              @NotNull String line,
                              @Nullable LogMessageType type,
                              @NotNull Consumer<? super BuildEvent> messageConsumer) {
    if (line.endsWith("java.lang.OutOfMemoryError")) {
      messageConsumer.accept(new MessageEventImpl(id, MessageEvent.Kind.ERROR, COMPILER_MESSAGES_GROUP,
                                                  "Out of memory.", line));
      return true;
    }
    return false;
  }
}
