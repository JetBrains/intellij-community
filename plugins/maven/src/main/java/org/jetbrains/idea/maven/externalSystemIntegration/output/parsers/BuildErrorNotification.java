// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output.parsers;

import com.intellij.build.FilePosition;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.MessageEvent;
import com.intellij.build.events.impl.FileMessageEventImpl;
import com.intellij.build.events.impl.MessageEventImpl;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.externalSystemIntegration.output.LogMessageType;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLogEntryReader;
import org.jetbrains.idea.maven.externalSystemIntegration.output.MavenLoggedEventParser;

import java.io.File;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BuildErrorNotification implements MavenLoggedEventParser {
  private static final Pattern LINE_AND_COLUMN = Pattern.compile("[^\\d]*?(\\d+)[^\\d]*(\\d*)[])]");
  private final String myLanguage;
  private final String myExtension;
  private final String myMessageGroup;

  protected BuildErrorNotification(String language, String extension, String messageGroup) {
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
                              @NotNull MavenLogEntryReader.MavenLogEntry logLine,
                              @NotNull MavenLogEntryReader logEntryReader,
                              @NotNull Consumer<? super BuildEvent> messageConsumer) {

    String line = logLine.getLine();
    if (line.endsWith("java.lang.OutOfMemoryError")) {
      messageConsumer.accept(new MessageEventImpl(parentId, MessageEvent.Kind.ERROR, myMessageGroup,
                                                  "Out of memory.", line));
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
    int start = SystemInfo.isWindows && line.charAt(0) == '/' ? 1 : 0;
    String filename = FileUtil.toSystemDependentName(line.substring(start, fileNameIdx) + "." + myExtension);

    File parsedFile = new File(filename);
    String lineWithPosition = line.substring(fullFileNameIdx);
    Matcher matcher = LINE_AND_COLUMN.matcher(lineWithPosition);
    String message;
    FilePosition position;
    if (matcher.find()) {
      position = withLineAndColumn(parsedFile, matcher);
      message = lineWithPosition.substring(matcher.end());
    }
    else {
      position = new FilePosition(parsedFile, 0, 0);
      message = lineWithPosition;
    }


    String errorMessage = getErrorMessage(position, message);
    messageConsumer
      .accept(new FileMessageEventImpl(parentId, MessageEvent.Kind.ERROR, myMessageGroup, errorMessage, errorMessage,
                                       position));
    return true;
  }

  @NotNull
  private String getErrorMessage(@NotNull FilePosition position, @NotNull String message) {
    if (position.getStartLine() == 0) {
      return "Error: " + myLanguage + ":" + message;
    }
    if (position.getStartColumn() == 0) {
      return "Error:(" + (position.getStartLine() + 1) + ") " + myLanguage + ":" + message;
    }
    return "Error:(" + (position.getStartLine() + 1) + "," + (position.getStartColumn() + 1) + ") " + myLanguage + ":" + message;
  }

  @NotNull
  private static FilePosition withLineAndColumn(File toTest, Matcher matcher) {
    try {
      if (matcher.groupCount() == 2) {
        if (matcher.start(2) < 0) {
          return new FilePosition(toTest, Integer.valueOf(matcher.group(1)) - 1, 0);
        }
        else {
          return new FilePosition(toTest, Integer.valueOf(matcher.group(1)) - 1, Integer.valueOf(matcher.group(2)) - 1);
        }
      }
      return new FilePosition(toTest, 0, 0);
    }
    catch (NumberFormatException ignore) {
      return new FilePosition(toTest, 0, 0);
    }
  }
}
