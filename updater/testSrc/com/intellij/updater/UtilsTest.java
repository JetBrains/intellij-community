// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@UpdaterTest
class UtilsTest {
  @TempDir Path tempDir;

  @Test void delete() throws Exception {
    var file = Files.createFile(tempDir.resolve("temp_file"));
    Utils.delete(file);
    assertThat(file).doesNotExist();
  }

  @Test void deleteReadonlyFile() throws Exception {
    var dir = Files.createDirectory(tempDir.resolve("temp_dir"));
    var file = Files.createFile(dir.resolve("temp_file"));
    UpdaterTestCase.setReadOnly(file);

    Utils.delete(dir);
    assertThat(dir).doesNotExist();
  }

  @Test @EnabledOnOs(OS.WINDOWS) void deleteLockedFileOnWindows() throws Exception {
    var file = Files.createFile(tempDir.resolve("temp_file"));
    var timing = new AtomicLong(0L);
    assertThatThrownBy(() -> {
      try (var os = Files.newOutputStream(file)) {
        // This locks the file on Windows, preventing it from being deleted. Utils.delete() will retry for about 100 ms.
        os.write("test".getBytes(StandardCharsets.UTF_8));
        var t = System.nanoTime();
        try {
          Utils.delete(file);
        }
        finally {
          timing.set(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t));
        }
      }
    }).hasMessage("Cannot delete: " + file.toAbsolutePath());
    assertThat(timing.get()).as("Utils.delete took " + timing + " ms, which is less than expected").isGreaterThanOrEqualTo(95);
  }

  @Test @DisabledOnOs(OS.WINDOWS) void deleteLockedFileOnUnix() throws Exception {
    var file = Files.createFile(tempDir.resolve("temp_file"));
    try (var os = Files.newOutputStream(file)) {
      os.write("test".getBytes(StandardCharsets.UTF_8));
      Utils.delete(file);
    }
  }

  @Test void recursiveDelete() throws Exception {
    var topDir = Files.createDirectory(tempDir.resolve("temp_dir"));
    for (var i = 0; i < 3; i++) {
      var subDir = Files.createDirectory(topDir.resolve("dir" + i));
      for (var j = 0; j < 3; j++) {
        Files.writeString(subDir.resolve("file" + j), "test");
      }
    }

    Utils.delete(topDir);
    assertThat(topDir).doesNotExist();
  }

  @Test void nonRecursiveSymlinkDelete() throws Exception {
    var dir = Files.createDirectory(tempDir.resolve("temp_dir"));
    var file = Files.createFile(dir.resolve("file"));
    var link = Files.createSymbolicLink(tempDir.resolve("link"), dir.getFileName());
    Utils.delete(link);
    assertThat(link).doesNotExist();
    assertThat(file).exists();
  }

  @Test void deleteDanglingSymlink() throws Exception {
    var dir = Files.createDirectory(tempDir.resolve("temp_dir"));
    var link = Files.createSymbolicLink(dir.resolve("link"), Path.of("dangling"));
    Utils.delete(link);
    assertThat(dir).isEmptyDirectory();
  }
}
