// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings("IO_FILE_USAGE")
class NativeFileManagerTest {
  @TempDir Path tempDir;
  private final ConsoleUpdaterUI testUI = new ConsoleUpdaterUI(false);

  @Test @EnabledOnOs(OS.WINDOWS) void singleProcessHoldsFile() throws IOException, InterruptedException {
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

  @Test @EnabledOnOs(OS.WINDOWS) void moreProcessesThanBufferSize() throws IOException, InterruptedException {

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

  @Test void fewHeldFilesNeedFewQueries() throws OperationCancelledException {
    var files = IntStream.range(0, 2501).mapToObj(i -> tempDir.resolve("file-" + i).toFile()).collect(Collectors.toList());

    var heldFiles = Set.of(files.get(0), files.get(files.size() / 2), files.get(files.size() - 1));
    var freeFiles = new ArrayList<>(files);
    freeFiles.removeAll(heldFiles);

    var queries = new AtomicInteger();
    var callingThread = Thread.currentThread();

    var unused = NativeFileManager.findUnusedFiles(files, testUI, group -> {
      assertThat(Thread.currentThread()).isSameAs(callingThread);
      queries.incrementAndGet();
      //noinspection SSBasedInspection
      return group.stream().anyMatch(heldFiles::contains)
             ? List.of(new NativeFileManager.Process(1, "holder"))
             : List.of();
    });

    assertThat(unused).containsExactlyInAnyOrderElementsOf(freeFiles);
    assertThat(queries.get()).isLessThan(100);
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
