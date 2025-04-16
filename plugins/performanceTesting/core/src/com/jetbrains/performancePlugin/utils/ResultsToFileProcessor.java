// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.utils;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public final class ResultsToFileProcessor {
  private static final Logger LOG = Logger.getInstance(ResultsToFileProcessor.class);

  /**
   * @param jsonPath path to store json
   * @param name metric base name without <code>count</code> or <code>time</code>
   * @param value usually amount of entries
   * @param startTime metric start time
   * <br>
   * Result JSON example:
   * <pre>
   * {@code
   * {
   * "instance_true_count":386,
   * "instance_true_time":505,
   * "instance_false_count":386,
   * "instance_false_time":16
   * }
   * }
   * </pre>
   * <code>_count</code> and <code>_time</code> are appended automatically
   */
  public static void writeMetricsToJson(Path jsonPath, String name, @Nullable Integer value, @Nullable Long startTime) {
    String stringToWrite;
    if (value != null && startTime != null) {
      stringToWrite = String.format("{\"%s_count\":%d,\"%1$s_time\":%d}", name, value, System.currentTimeMillis() - startTime);
    }
    else if (startTime != null) {
      stringToWrite = String.format("{\"%s_time\":%d}", name, System.currentTimeMillis() - startTime);
    }
    else if (value != null) {
      stringToWrite = String.format("{\"%s_count\":%d}", name, value);
    }
    else {
      LOG.error("value or startTime has to be provided");
      return;
    }

    try {
      File file = jsonPath.toFile();
      if (file.length() != 0) {
        String fileContent = String.valueOf(FileUtil.loadFileText(file)).replace("\n", "").replace("\t", "");
        stringToWrite = new String(fileContent.substring(0, fileContent.length() - 1) +
                                   stringToWrite.replace("{", ","));
      }
      FileUtil.writeToFile(file, stringToWrite);
    }
    catch (IOException ex) {
      LOG.error("Could not create json file " + jsonPath + " with the performance metrics.");
    }
  }
}


