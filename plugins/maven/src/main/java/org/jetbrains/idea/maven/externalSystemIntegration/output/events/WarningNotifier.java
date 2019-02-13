// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.events;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.MessageEventImpl;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser;

import java.util.Set;
import java.util.function.Consumer;

public class WarningNotifier implements MavenLoggedEventParser {

  private final Set<String> warnings = ContainerUtil.newHashSet();

  @Override
  public boolean supportsType(@Nullable LogMessageType type) {
    return type == LogMessageType.WARNING;
  }

  @Override
  public boolean checkLogLine(@NotNull ExternalSystemTaskId id,
                              @NotNull String line,
                              @Nullable LogMessageType type,
                              @NotNull Consumer<? super BuildEvent> messageConsumer) {

    if (warnings.add(line)) {
      messageConsumer.accept(new MessageEventImpl(id, MessageEvent.Kind.WARNING, "Warning", line, line));
      return true;
    }
    return false;
  }
}
