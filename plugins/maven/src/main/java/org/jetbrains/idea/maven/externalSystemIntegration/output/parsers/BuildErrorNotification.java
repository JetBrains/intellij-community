// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import com.intellij.build.FilePosition;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.BuildEventsNls;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.FileMessageEventImpl;
import com.intellij.build.events.impl.MessageEventImpl;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.RunnerBundle;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenParsingContext;

import java.io.File;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BuildErrorNotification implements MavenLoggedEventParser {
  private static final Pattern LINE_AND_COLUMN = Pattern.compile("[^\\d]+?(\\d+)[^\\d]+(\\d+)");
  private static final Pattern LINE_ONLY = Pattern.compile("[^\\d]+?(\\d+)");
  private final String myLanguage;
  private final String myExtension;
  private @BuildEventsNls.Title final String myMessageGroup;

  protected BuildErrorNotification(@NonNls String language, @NonNls String extension, @BuildEventsNls.Title String messageGroup) {
    myLanguage = language;
    myExtension = extension;
    myMessageGroup = messageGroup;
  }

  @Override
  public boolean supportsType(@Nullable LogMessageType type) {
    return type == LogMessageType.ERROR;
  }

  @Override
  public boolean checkLogLine(@NotNull Object parentId,
                              @NotNull MavenParsingContext parsingContext,
                              @NotNull MavenLogEntryReader.MavenLogEntry logLine,
                              @NotNull MavenLogEntryReader logEntryReader,
                              @NotNull Consumer<? super BuildEvent> messageConsumer) {

    String line = logLine.getLine();
    if (line.endsWith("java.lang.OutOfMemoryError")) {
      messageConsumer.accept(new MessageEventImpl(parentId, MessageEvent.Kind.ERROR, myMessageGroup,
                                                  RunnerBundle.message("build.event.message.out.memory"), line));
      return true;
    }
    int fileNameIdx = line.indexOf("." + myExtension + ":");
    if (fileNameIdx < 0) {
      return false;
    }
    int fullFileNameIdx = line.indexOf(":", fileNameIdx);
    if (fullFileNameIdx < 0) {
      return false;
    }
    String targetFileNameWithoutExtension = line.substring(0, fileNameIdx);
    String localFileNameWithoutExtension = parsingContext.getTargetFileMapper().apply(targetFileNameWithoutExtension);
    String filename = FileUtil.toSystemDependentName(localFileNameWithoutExtension + "." + myExtension);

    File parsedFile = new File(filename);
    String lineWithPosition = line.substring(fullFileNameIdx);
    Matcher matcher = getMatcher(lineWithPosition);
    String message;
    FilePosition position;

    if (matcher == null) {
      position = new FilePosition(parsedFile, 0, 0);
      message = lineWithPosition;
    }
    else {
      position = withLineAndColumn(parsedFile, matcher);
      message = lineWithPosition.substring(matcher.end());
    }

    String errorMessage = getErrorMessage(position, message);
    messageConsumer
      .accept(new FileMessageEventImpl(parentId, MessageEvent.Kind.ERROR, myMessageGroup, errorMessage, errorMessage,
                                       position));
    return true;
  }

  private Matcher getMatcher(String string) {
    Matcher result = LINE_AND_COLUMN.matcher(string);
    if (result.lookingAt()) {
      return result;
    }
    result = LINE_ONLY.matcher(string);
    if (result.lookingAt()) {
      return result;
    }
    return null;
  }

  @NotNull
  @NlsSafe
  private String getErrorMessage(@NotNull FilePosition position, @NotNull String message) {
    message = message.trim();
    while (message.startsWith(":") || message.startsWith("]") || message.startsWith(")")) {
      message = message.substring(1);
    }
    message = message.trim();

    return message;
  }

  @NotNull
  private static FilePosition withLineAndColumn(File toTest, Matcher matcher) {
    if (matcher.groupCount() == 2) {
      return new FilePosition(toTest, atoi(matcher.group(1)) - 1, atoi(matcher.group(2)) - 1);
    }
    else if (matcher.groupCount() == 1) {
      return new FilePosition(toTest, atoi(matcher.group(1)) - 1, 0);
    }
    else {
      return new FilePosition(toTest, 0, 0);
    }
  }

  private static int atoi(String s) {
    try {
      return Integer.parseInt(s);
    }
    catch (NumberFormatException ignore) {
      return 0;
    }
  }
}
