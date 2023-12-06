/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.project;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ExceptionUtil;
import org.jetbrains.idea.maven.execution.MavenExecutionOptions;
import org.jetbrains.idea.maven.server.MavenServerConsoleIndicator;

import java.text.MessageFormat;

/**
 * @deprecated Use MavenSyncConsole instead
 */
@Deprecated
public abstract class MavenConsole {
  private static final String LINE_SEPARATOR = System.lineSeparator();

  public enum OutputType {
    NORMAL, SYSTEM, ERROR
  }

  private final int myOutputLevel;

  private static final BiMap<String, Integer> PREFIX_TO_LEVEL = ImmutableBiMap.of(
    "DEBUG", MavenServerConsoleIndicator.LEVEL_DEBUG,
    "INFO", MavenServerConsoleIndicator.LEVEL_INFO,
    "WARNING", MavenServerConsoleIndicator.LEVEL_WARN,
    "ERROR", MavenServerConsoleIndicator.LEVEL_ERROR,
    "FATAL_ERROR", MavenServerConsoleIndicator.LEVEL_FATAL
  );

  @Deprecated(forRemoval = true)
  public MavenConsole(MavenExecutionOptions.LoggingLevel outputLevel, boolean ignored) {
    this(outputLevel);
  }

  public MavenConsole(MavenExecutionOptions.LoggingLevel outputLevel) {
    myOutputLevel = outputLevel.getLevel();
  }

  private boolean isSuppressed(int level) {
    return level < myOutputLevel;
  }

  @Deprecated(forRemoval = true)
  public boolean isSuppressed(String line) {
    return isSuppressed(getLevel(line));
  }

  public void systemMessage(int level, String string, Throwable throwable) {
    printMessage(level, string, throwable);
  }

  private void printMessage(int level, String string, Throwable throwable) {
    if (isSuppressed(level)) return;

    OutputType type = OutputType.NORMAL;
    if (throwable != null
        || level == MavenServerConsoleIndicator.LEVEL_WARN
        || level == MavenServerConsoleIndicator.LEVEL_ERROR
        || level == MavenServerConsoleIndicator.LEVEL_FATAL) {
      type = OutputType.ERROR;
    }

    doPrint(composeLine(level, string), type);

    if (throwable != null) {
      String throwableText = ExceptionUtil.getThrowableText(throwable);
      if (Registry.is("maven.print.import.stacktraces") || ApplicationManager.getApplication().isUnitTestMode()) { //NO-UT-FIX
        doPrint(LINE_SEPARATOR + composeLine(MavenServerConsoleIndicator.LEVEL_ERROR, throwableText), type);
      }
      else {
        doPrint(LINE_SEPARATOR + composeLine(MavenServerConsoleIndicator.LEVEL_ERROR, throwable.getMessage()), type);
      }
    }
  }

  protected abstract void doPrint(String text, OutputType type);

  private static int getLevel(String line) {
    return getLevelByPrefix(extractPrefix(line));
  }

  private static String extractPrefix(String line) {
    if (line.startsWith("[")) {
      int closing = line.indexOf("] ", 1);
      if (closing > 1) {
        return line.substring(1, closing);
      }
    }
    return "";
  }

  private static int getLevelByPrefix(String prefix) {
    Integer level = PREFIX_TO_LEVEL.get(prefix);
    return level != null ? level : MavenServerConsoleIndicator.LEVEL_WARN;
  }

  private static String composeLine(int level, String message) {
    return MessageFormat.format("[{0}] {1}", getPrefixByLevel(level), message);
  }

  private static String getPrefixByLevel(int level) {
    return PREFIX_TO_LEVEL.inverse().get(level);
  }
}
