// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.build.events.BuildEvent;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Log line parser for maven task execution process (maven build log).
 * Example of use:
 * override fun checkLogLine(...): Boolean {
 *     if (logLine.line.contains("error1") && logEntryReader.readLine().line.contains("error2")) {
 *       messageConsumer.accept(BuildIssueEventImpl(parentId, BuildIssue(...), MessageEvent.Kind.ERROR));
 *       return true
 *     }
 *     return false
 *   }
 *
 * {@link MavenLogOutputParser)
 */
@ApiStatus.Experimental
public interface MavenLoggedEventParser {
  ExtensionPointName<MavenLoggedEventParser> EP_NAME = ExtensionPointName.create("org.jetbrains.idea.maven.log.parser");

  boolean supportsType(@Nullable LogMessageType type);

  /**
   * Process log line.
   * @param parentId - node id from BuildTreeConsoleView.
   * @param parsingContext - maven parsing context.
   * @param logLine - log line text.
   * @param messageConsumer build event consumer.
   * @return true if log line consumed.
   */
  boolean checkLogLine(@NotNull Object parentId,
                       @NotNull MavenParsingContext parsingContext,
                       @NotNull MavenLogEntryReader.MavenLogEntry logLine,
                       @NotNull MavenLogEntryReader logEntryReader,
                       @NotNull Consumer<? super BuildEvent> messageConsumer);


  /**
   * Callback when maven task execution process is finished.
   */
  default void finish(@NotNull ExternalSystemTaskId taskId, @NotNull Consumer<? super BuildEvent> messageConsumer) {}
}
