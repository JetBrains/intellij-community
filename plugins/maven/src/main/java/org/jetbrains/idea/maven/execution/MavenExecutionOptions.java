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

import org.jetbrains.idea.maven.server.MavenServerConsole;
import org.jetbrains.idea.maven.server.MavenServerSettings;

public class MavenExecutionOptions {
  public enum LoggingLevel {
    DEBUG("Debug", MavenServerConsole.LEVEL_DEBUG),
    INFO("Info", MavenServerConsole.LEVEL_INFO),
    WARN("Warn", MavenServerConsole.LEVEL_WARN),
    ERROR("Error", MavenServerConsole.LEVEL_ERROR),
    FATAL("Fatal", MavenServerConsole.LEVEL_FATAL),
    DISABLED("Disabled", MavenServerConsole.LEVEL_DISABLED);

    private final String myDisplayString;
    private final int myLevel;

    LoggingLevel(String displayString, int level) {
      myDisplayString = displayString;
      myLevel = level;
    }

    public String getDisplayString() {
      return myDisplayString;
    }

    public int getLevel() {
      return myLevel;
    }
  }

  public enum FailureMode {
    NOT_SET("Default", ""), FAST("Fail Fast", "--fail-fast"), AT_END("Fail At End", "--fail-at-end"), NEVER("Never Fail", "--fail-never");

    private final String myDisplayString;
    private final String myCommandLineOption;

    FailureMode(String displayString, String commandLineOption) {
      myDisplayString = displayString;
      myCommandLineOption = commandLineOption;
    }

    public String getDisplayString() {
      return myDisplayString;
    }

    public String getCommandLineOption() {
      return myCommandLineOption;
    }
  }

  public enum ChecksumPolicy {
    NOT_SET("No Global Policy", ""),
    FAIL("Fail", "--strict-checksums"),
    WARN("Warn", "--lax-checksums");

    private final String myDisplayString;
    private final String myCommandLineOption;

    ChecksumPolicy(String displayString, String commandLineOption) {
      myDisplayString = displayString;
      myCommandLineOption = commandLineOption;
    }

    public String getDisplayString() {
      return myDisplayString;
    }

    public String getCommandLineOption() {
      return myCommandLineOption;
    }
  }

  public enum PluginUpdatePolicy {
    UPDATE("Check For Updates", "--check-plugin-updates", MavenServerSettings.UpdatePolicy.ALWAYS_UPDATE),
    DO_NOT_UPDATE("Do Not Update", "--no-plugin-updates", MavenServerSettings.UpdatePolicy.DO_NOT_UPDATE),
    DEFAULT("Default", "", MavenServerSettings.UpdatePolicy.DO_NOT_UPDATE);

    private final String myDisplayString;
    private final String myCommandLineOption;
    private final MavenServerSettings.UpdatePolicy myServerPolicy;

    PluginUpdatePolicy(String displayString, String commandLineOption, MavenServerSettings.UpdatePolicy policy) {
      myDisplayString = displayString;
      myCommandLineOption = commandLineOption;
      myServerPolicy = policy;
    }

    public String getDisplayString() {
      return myDisplayString;
    }

    public String getCommandLineOption() {
      return myCommandLineOption;
    }

    public MavenServerSettings.UpdatePolicy getServerPolicy() {
      return myServerPolicy;
    }
  }
}
