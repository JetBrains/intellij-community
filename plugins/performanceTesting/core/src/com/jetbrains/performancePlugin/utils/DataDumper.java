// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.utils;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.kotlin.ExtensionsKt;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Path;

public final class DataDumper {
  private static final Logger LOG = Logger.getInstance(DataDumper.class);
  public static final ObjectMapper objectMapper = ExtensionsKt.jacksonObjectMapper()
    .setDefaultPrettyPrinter(new DefaultPrettyPrinter());

  public static void dump(@NotNull Object file, @NotNull Path path) {
    try {
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), file);
    }
    catch (IOException e) {
      throw new RuntimeException("Failed to write data to " + path, e);
    }
  }

  public static <T> T read(@NotNull Path path, @NotNull Class<T> tClass) {
    try {
      return objectMapper.readValue(path.toFile(), tClass);
    }
    catch (IOException e) {
      throw new RuntimeException("Fail to read data from file " + path, e);
    }
  }

}
