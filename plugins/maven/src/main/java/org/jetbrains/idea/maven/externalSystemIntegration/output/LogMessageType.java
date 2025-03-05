// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.externalSystemIntegration.output;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public enum LogMessageType {
  INFO("[INFO] "),
  WARNING("[WARNING] "),
  ERROR("[ERROR] ");

  private final String myPrefix;
  private final int myPrefixLength;

  LogMessageType(final String prefix) {
    myPrefix = prefix;
    myPrefixLength = myPrefix.length();
  }

  @NotNull
  String clearLine(@NotNull String line) {
    return line.substring(myPrefixLength);
  }

  static @Nullable LogMessageType determine(@NotNull String line) {
    for (LogMessageType type : LogMessageType.values()) {
      if (line.startsWith(type.myPrefix)) {
        return type;
      }
    }
    return null;
  }
}
