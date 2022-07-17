// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.skeleton;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PySkeletonHeader {
  @NonNls public static final String BUILTIN_NAME = "(built-in)"; // version required for built-ins
  @NonNls public static final String PREGENERATED = "(pre-generated)"; // pre-generated skeleton

  // Path (the first component) may contain spaces, this header spec is deprecated
  private static final Pattern VERSION_LINE_V1 = Pattern.compile("# from (\\S+) by generator (\\S+)\\s*");
  // Skeleton header spec v2
  private static final Pattern FROM_LINE_V2 = Pattern.compile("# from (.*)$");
  private static final Pattern BY_LINE_V2 = Pattern.compile("# by generator (.*)$");

  @NotNull private final String myFile;
  private final int myVersion;

  public PySkeletonHeader(@NotNull String binaryFile, int version) {
    myFile = binaryFile;
    myVersion = version;
  }

  @NotNull
  public String getBinaryFile() {
    return myFile;
  }

  public int getVersion() {
    return myVersion;
  }

  @Nullable
  public static PySkeletonHeader readSkeletonHeader(@NotNull File file) {
    try (LineNumberReader reader = new LineNumberReader(new FileReader(file))) {
      String line = null;
      // Read 3 lines, skip first 2: encoding, module name
      for (int i = 0; i < 3; i++) {
        line = reader.readLine();
        if (line == null) {
          return null;
        }
      }
      // Try the old whitespace-unsafe header format v1 first
      final Matcher v1Matcher = VERSION_LINE_V1.matcher(line);
      if (v1Matcher.matches()) {
        return new PySkeletonHeader(v1Matcher.group(1), fromVersionString(v1Matcher.group(2)));
      }
      final Matcher fromMatcher = FROM_LINE_V2.matcher(line);
      if (fromMatcher.matches()) {
        final String binaryFile = fromMatcher.group(1);
        line = reader.readLine();
        if (line != null) {
          final Matcher byMatcher = BY_LINE_V2.matcher(line);
          if (byMatcher.matches()) {
            final int version = fromVersionString(byMatcher.group(1));
            return new PySkeletonHeader(binaryFile, version);
          }
        }
      }
    }
    catch (IOException ignored) {
    }
    return null;
  }

  /**
   * Transforms a string like "1.2" into an integer representing it.
   *
   * @param input
   * @return an int representing the version: major number shifted 8 bit and minor number added. or 0 if version can't be parsed.
   */
  public static int fromVersionString(final String input) {
    int dot_pos = input.indexOf('.');
    try {
      if (dot_pos > 0) {
        int major = Integer.parseInt(input.substring(0, dot_pos));
        int minor = Integer.parseInt(input.substring(dot_pos + 1));
        return (major << 8) + minor;
      }
    }
    catch (NumberFormatException ignore) {
    }
    return 0;
  }
}
