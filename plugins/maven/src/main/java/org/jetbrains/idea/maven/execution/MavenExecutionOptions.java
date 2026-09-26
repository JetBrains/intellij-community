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
package org.jetbrains.idea.maven.execution;

import org.jetbrains.idea.maven.server.MavenServerConsoleIndicator;

public class MavenExecutionOptions {
  public enum LoggingLevel {
    DEBUG("maven.log.level.debug", MavenServerConsoleIndicator.LEVEL_DEBUG),
    INFO("maven.log.level.info", MavenServerConsoleIndicator.LEVEL_INFO),
    WARN("maven.log.level.warn", MavenServerConsoleIndicator.LEVEL_WARN),
    ERROR("maven.log.level.error", MavenServerConsoleIndicator.LEVEL_ERROR),
    FATAL("maven.log.level.fatal", MavenServerConsoleIndicator.LEVEL_FATAL),
    DISABLED("maven.log.level.disabled", MavenServerConsoleIndicator.LEVEL_DISABLED);

    private final String myMessageKey;
    private final int myLevel;

    LoggingLevel(String messageKey, int level) {
      myMessageKey = messageKey;
      myLevel = level;
    }

    public String getDisplayString() {
      return RunnerBundle.message(myMessageKey);
    }

    public int getLevel() {
      return myLevel;
    }
  }

  public enum FailureMode {
    NOT_SET("maven.failure.mode.default", ""), FAST("maven.failure.mode.failfast", "--fail-fast"), AT_END("maven.failure.mode.failend", "--fail-at-end"), NEVER("maven.failure.mode.never", "--fail-never");

    private final String myMessageKey;
    private final String myCommandLineOption;

    FailureMode(String messageKey, String commandLineOption) {
      myMessageKey = messageKey;
      myCommandLineOption = commandLineOption;
    }

    public String getDisplayString() {
      return RunnerBundle.message(myMessageKey);
    }

    public String getCommandLineOption() {
      return myCommandLineOption;
    }
  }

  public enum ChecksumPolicy {
    NOT_SET("maven.checksum.nopolicy", ""),
    FAIL("maven.checksum.fail", "--strict-checksums"),
    WARN("maven.checksum.warn", "--lax-checksums");

    private final String myMessageKey;
    private final String myCommandLineOption;

    ChecksumPolicy(String messageKey, String commandLineOption) {
      myMessageKey = messageKey;
      myCommandLineOption = commandLineOption;
    }

    public String getDisplayString() {
      return RunnerBundle.message(myMessageKey);
    }

    public String getCommandLineOption() {
      return myCommandLineOption;
    }
  }

}
