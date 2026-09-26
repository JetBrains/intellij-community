// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import com.intellij.build.events.BuildEvent;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.externalSystemIntegration.output.parsers.MavenEventType;

import java.util.function.Consumer;

/**
 * Log line parser for maven spy log - task execution process.
 * {@link MavenSpyOutputParser)
 */
@ApiStatus.Experimental
public interface MavenSpyLoggedEventParser {
  ExtensionPointName<MavenSpyLoggedEventParser> EP_NAME = ExtensionPointName.create("org.jetbrains.idea.maven.log.spy.parser");

  boolean supportsType(@NotNull MavenEventType type);

  /**
   * Process log line.
   * @param parentId - node id from BuildTreeConsoleView.
   * @param parsingContext - maven parsing context.
   * @param logLine - log line text.
   * @param messageConsumer build event consumer.
   * @return true if log line consumed.
   */
  boolean processLogLine(
    @NotNull Object parentId,
    @NotNull MavenParsingContext parsingContext,
    @NotNull String logLine,
    @NotNull Consumer<? super BuildEvent> messageConsumer);
}
