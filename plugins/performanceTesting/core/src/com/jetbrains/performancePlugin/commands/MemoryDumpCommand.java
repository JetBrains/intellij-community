// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands;

import com.intellij.openapi.ui.playback.PlaybackContext;
import com.intellij.openapi.ui.playback.commands.AbstractCommand;
import com.intellij.util.MemoryDumpHelper;
import com.jetbrains.performancePlugin.LogDirHandler;
import com.jetbrains.performancePlugin.Timer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Command dumps memory
 */
public final class MemoryDumpCommand extends AbstractCommand {
  public static final String PREFIX = CMD_PREFIX + "memoryDump";

  public MemoryDumpCommand(@NotNull String text, int line) {
    super(text, line);
  }

  @Override
  protected @NotNull Promise<Object> _execute(@NotNull PlaybackContext context) {
    try {
      Path path = getMemoryDumpPath();
      captureZippedMemoryDump(path.toString());
      context.message("Memory snapshot is saved at " + path, getLine());
      return Promises.resolvedPromise();
    }
    catch (Exception e) {
      return Promises.rejectedPromise("Memory dump can't be collected");
    }
  }

  public static @NotNull Path getMemoryDumpPath() {
    String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    return LogDirHandler.currentLogDir().resolve(Timer.instance.getActivityName() + '-' + currentTime + ".zip");
  }

  public static void captureZippedMemoryDump(String dumpPath) throws Exception {
    if (!MemoryDumpHelper.memoryDumpAvailable()) {
      throw new RuntimeException("Memory dump is not available");
    }
    //noinspection CallToSystemGC
    System.gc();
    MemoryDumpHelper.captureMemoryDumpZipped(dumpPath);
  }
}
