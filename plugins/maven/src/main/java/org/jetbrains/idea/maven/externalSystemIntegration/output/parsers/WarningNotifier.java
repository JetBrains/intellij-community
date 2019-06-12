// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.MessageEventImpl;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class WarningNotifier implements MavenLoggedEventParser {

  private final Set<String> warnings = new HashSet<>();

  @Override
  public boolean supportsType(@Nullable LogMessageType type) {
    return type == LogMessageType.WARNING;
  }

  @Override
  public boolean checkLogLine(@NotNull Object parendId,
                              @NotNull MavenLogEntryReader.MavenLogEntry logLine,
                              @NotNull MavenLogEntryReader logEntryReader,
                              @NotNull Consumer<? super BuildEvent> messageConsumer) {

    String line = logLine.getLine();


    List<MavenLogEntryReader.MavenLogEntry> toConcat = logEntryReader.readWhile(l -> l.getType() == LogMessageType.WARNING);
    String contatenated = line + "\n" + StringUtil.join(toConcat, MavenLogEntryReader.MavenLogEntry::getLine, "\n");
    String message = getMessage(line, toConcat);
    if (!StringUtil.isEmptyOrSpaces(message) && warnings.add(message)) {
      messageConsumer.accept(new MessageEventImpl(parendId, MessageEvent.Kind.WARNING, "Warning", message, contatenated));
      return true;
    }
    return false;
  }

  @NotNull
  private static String getMessage(String line, List<MavenLogEntryReader.MavenLogEntry> toConcat) {
    if (toConcat == null || toConcat.isEmpty()) {
      return line;
    }
    if (!StringUtil.isEmptyOrSpaces(line)) {
      return line;
    }
    MavenLogEntryReader.MavenLogEntry entry = ContainerUtil.find(toConcat, e -> !StringUtil.isEmptyOrSpaces(e.getLine()));
    if (entry != null) {
      return entry.getLine();
    }
    return "";
  }
}
