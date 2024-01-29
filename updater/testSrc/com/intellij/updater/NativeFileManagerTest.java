// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class NativeFileManagerTest {

  @SuppressWarnings("SSBasedInspection") // suppress inspections about IntelliJ-only API
  @Test public void singleProcessHoldsFile() throws IOException, InterruptedException {
    if (!Utils.IS_WINDOWS) return;

    var testFile = File.createTempFile("process-locked-test", ".tmp");
    try {
      var process = startProcessHoldingFile(testFile);
      try {
        var holders = NativeFileManager.getProcessesUsing(testFile).stream()
          .map(p -> (long)p.pid).collect(Collectors.toList());
        assertThat(holders).containsExactly(process.pid());
      } finally {
        process.destroy();
        process.waitFor();
      }

      assertThat(NativeFileManager.getProcessesUsing(testFile)).isEmpty();
    } finally {
      var ignored = testFile.delete();
    }
  }

  @SuppressWarnings("SSBasedInspection") // suppress inspections about IntelliJ-only API
  @Test public void moreProcessesThanBufferSize() throws IOException, InterruptedException {
    if (!Utils.IS_WINDOWS) return;

    final int processCount = 5;
    final int bufferSize = processCount - 1; // check that we still receive all the processes even with smaller buffer size

    var testFile = File.createTempFile("process-locked-test", ".tmp");
    try {
      var processes = new Process[processCount];
      for (int i = 0; i < processCount; i++) {
        processes[i] = startProcessHoldingFile(testFile);
      }
      try {
        var expectedPids = Arrays.stream(processes).map(Process::pid).collect(Collectors.toList());

        var holders = NativeFileManager.getProcessesUsing(testFile, bufferSize).stream()
          .map(p -> (long)p.pid).collect(Collectors.toList());
        assertThat(holders).hasSize(processCount);
        assertThat(holders).containsAll(expectedPids);
      } finally {
        for (var process : processes) {
          process.destroy();
          process.waitFor();
        }
      }

      assertThat(NativeFileManager.getProcessesUsing(testFile)).isEmpty();
    } finally {
      var ignored = testFile.delete();
    }
  }

  private static Process startProcessHoldingFile(File file) throws IOException {
    var command = String.format(
      "$handle = [IO.File]::Open('%s', [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::ReadWrite); Write-Output 'ok'; Start-Sleep 10",
      file);
    var process = new ProcessBuilder("powershell.exe", "-Command", command).start();

    // Now, synchronize with the holder process: it will print "ok" after it got the file handle.
    try (var scanner = new Scanner(process.getInputStream(), StandardCharsets.UTF_8)) {
      var line = scanner.nextLine();
      assertThat(line).isEqualTo("ok");
    }

    return process;
  }
}
