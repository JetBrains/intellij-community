// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@UpdaterTest
class DigesterTest {
  @UpdaterTestData Path dataDir;

  @Test void basics() throws Exception {
    var binDir = dataDir.resolve("bin");
    var libDir = dataDir.resolve("lib");

    assertThat(Digester.digest(binDir)).isEqualTo(Digester.DIRECTORY);
    assertThat(Digester.digest(libDir.resolve("annotations.jar"))).isEqualTo(UpdaterTestCase.ANNOTATIONS_JAR);
    assertThat(Digester.digest(libDir.resolve("annotations_changed.jar"))).isEqualTo(UpdaterTestCase.ANNOTATIONS_CHANGED_JAR);
    assertThat(Digester.digest(libDir.resolve("boot.jar"))).isEqualTo(UpdaterTestCase.BOOT_JAR);
    assertThat(Digester.digest(libDir.resolve("boot_with_directory_becomes_file.jar"))).isEqualTo(UpdaterTestCase.BOOT_CHANGED_JAR);
    assertThat(Digester.digest(libDir.resolve("bootstrap.jar"))).isEqualTo(UpdaterTestCase.BOOTSTRAP_JAR);
    assertThat(Digester.digest(libDir.resolve("bootstrap_deleted.jar"))).isEqualTo(UpdaterTestCase.BOOTSTRAP_DELETED_JAR);
  }

  @Test void helpers() {
    assertThat(Digester.isFile(UpdaterTestCase.README_TXT)).isTrue();
    assertThat(Digester.isFile(UpdaterTestCase.ANNOTATIONS_JAR)).isTrue();
    assertThat(Digester.isFile(Digester.INVALID)).isFalse();
    assertThat(Digester.isFile(Digester.DIRECTORY)).isFalse();

    assertThat(Digester.isSymlink(UpdaterTestCase.LINK_TO_README_TXT)).isTrue();
    assertThat(Digester.isSymlink(UpdaterTestCase.README_TXT)).isFalse();
    assertThat(Digester.isSymlink(Digester.INVALID)).isFalse();
    assertThat(Digester.isSymlink(Digester.DIRECTORY)).isFalse();
  }

  @Test void symlinks(@TempDir Path tempDir) throws Exception {
    var simpleLink = Files.createSymbolicLink(tempDir.resolve("Readme.simple.link"), Path.of("Readme.txt"));
    assertThat(Digester.digest(simpleLink)).isEqualTo(UpdaterTestCase.LINK_TO_README_TXT);

    var relativeLink = Files.createSymbolicLink(tempDir.resolve("Readme.relative.link"), Path.of("./Readme.txt"));
    assertThat(Digester.digest(relativeLink))
      .isEqualTo(File.separatorChar == '\\' ? UpdaterTestCase.LINK_TO_DOT_README_TXT_DOS : UpdaterTestCase.LINK_TO_DOT_README_TXT_UNIX);

    var absoluteLink = Files.createSymbolicLink(tempDir.resolve("Readme.absolute.link"), dataDir.resolve("Readme.txt"));
    assertThatThrownBy(() -> Digester.digest(absoluteLink))
      .isInstanceOf(IOException.class)
      .hasMessageStartingWith("An absolute link");
  }

  @Test @DisabledOnOs(OS.WINDOWS) void executables(@TempDir Path tempDir) throws Exception {
    var testFile = Files.copy(dataDir.resolve("bin/idea.bat"), tempDir.resolve("idea.bat"));
    assertThat(Digester.digest(testFile)).isEqualTo(UpdaterTestCase.IDEA_BAT);
    Files.setPosixFilePermissions(testFile, PosixFilePermissions.fromString("r-xr-xr-x"));
    assertThat(Digester.digest(testFile)).isEqualTo(UpdaterTestCase.IDEA_BAT | Digester.EXECUTABLE);
  }
}
