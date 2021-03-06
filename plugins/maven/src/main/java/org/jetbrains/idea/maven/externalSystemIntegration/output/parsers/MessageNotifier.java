// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.BuildEventsNls;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.MessageEventImpl;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public abstract class MessageNotifier implements MavenLoggedEventParser {

  @NotNull private final LogMessageType myType;
  @NotNull private final MessageEvent.Kind myKind;
  @NotNull @BuildEventsNls.Title private final String myGroup;
  private final Set<String> myMessages = new HashSet<>();
  protected MessageNotifier(@NotNull LogMessageType type, @NotNull MessageEvent.Kind kind, @NotNull @BuildEventsNls.Title String group) {

    myType = type;
    myKind = kind;
    myGroup = group;
  }

  @Override
  public boolean supportsType(@Nullable LogMessageType type) {
    return type == myType;
  }

  @Override
  public boolean checkLogLine(@NotNull Object parendId,
                              @NotNull MavenParsingContext parsingContext,
                              @NotNull MavenLogEntryReader.MavenLogEntry logLine,
                              @NotNull MavenLogEntryReader logEntryReader,
                              @NotNull Consumer<? super BuildEvent> messageConsumer) {

    String line = logLine.getLine();


    List<MavenLogEntryReader.MavenLogEntry> toConcat = logEntryReader.readWhile(l -> l.getType() == myType);
    String contatenated = line + "\n" + StringUtil.join(toConcat, MavenLogEntryReader.MavenLogEntry::getLine, "\n");
    String message = getMessage(line, toConcat);
    if (!StringUtil.isEmptyOrSpaces(message) && myMessages.add(message)) {
      messageConsumer.accept(new MessageEventImpl(parendId, myKind, myGroup, message, contatenated));
      return true;
    }
    return false;
  }

  @NotNull
  @NlsSafe
  protected String getMessage(String line, List<MavenLogEntryReader.MavenLogEntry> toConcat) {
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
