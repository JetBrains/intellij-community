package org.jetbrains.idea.maven.externalSystemIntegration.output.events;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.MessageEventImpl;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser;

import java.util.function.Consumer;

public class CommonErrorParser implements MavenLoggedEventParser {
  @Override
  public boolean supportsType(@Nullable LogMessageType type) {
    return type == LogMessageType.ERROR;
  }

  @Override
  public boolean checkLogLine(@NotNull ExternalSystemTaskId id,
                              @NotNull MavenLogEntryReader.MavenLogEntry logLine,
                              @NotNull MavenLogEntryReader logEntryReader,
                              @NotNull Consumer<? super BuildEvent> messageConsumer) {
    String line = logLine.getLine().trim();
    line = StringUtil.trimEnd(line, ":");
    messageConsumer
      .accept(new MessageEventImpl(id, MessageEvent.Kind.ERROR, null, line, line));
    return true;
  }
}
