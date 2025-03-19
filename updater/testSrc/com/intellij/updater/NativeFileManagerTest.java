// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class NativeFileManagerTest {
  @TempDir Path tempDir;

  @Test void singleProcessHoldsFile() throws IOException, InterruptedException {
    assumeTrue(Utils.IS_WINDOWS);

    var testFile = Files.writeString(tempDir.resolve("process-locked-test.tmp"), "");
    var process = startProcessHoldingFile(testFile);
    try {
      var holders = NativeFileManager.getProcessesUsing(testFile.toFile()).stream().mapToLong(p -> (long)p.pid).toArray();
      assertThat(holders).containsExactly(process.pid());
    }
    finally {
      process.destroy();
      process.waitFor();
    }

    assertThat(NativeFileManager.getProcessesUsing(testFile.toFile())).isEmpty();
  }

  @Test void moreProcessesThanBufferSize() throws IOException, InterruptedException {
    assumeTrue(Utils.IS_WINDOWS);

    var processCount = 5;
    var bufferSize = processCount - 1; // check that we still receive all the processes even with smaller buffer size

    var testFile = Files.writeString(tempDir.resolve("process-locked-test.tmp"), "");
    var processes = new Process[processCount];
    for (int i = 0; i < processCount; i++) {
      processes[i] = startProcessHoldingFile(testFile);
    }
    try {
      var expectedPids = Arrays.stream(processes).mapToLong(Process::pid).toArray();
      var holders = NativeFileManager.getProcessesUsing(testFile.toFile(), bufferSize).stream().mapToLong(p -> (long)p.pid).toArray();
      assertThat(holders).hasSize(processCount).contains(expectedPids);
    }
    finally {
      for (var process : processes) {
        process.destroy();
        process.waitFor();
      }
    }

    assertThat(NativeFileManager.getProcessesUsing(testFile.toFile())).isEmpty();
  }

  private static Process startProcessHoldingFile(Path file) throws IOException {
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
