// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.openapi.util.io.NioFiles;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.Assume.assumeFalse;

public class DigesterTest extends UpdaterTestCase {
  @Test
  public void testBasics() throws Exception {
    Path binDir = dataDir.toPath().resolve("bin"), libDir = dataDir.toPath().resolve("lib");

    assertThat(Digester.digest(binDir)).isEqualTo(Digester.DIRECTORY);
    assertThat(Digester.digest(libDir)).isEqualTo(Digester.DIRECTORY);

    assertThat(Digester.digest(dataDir.toPath().resolve("Readme.txt"))).isEqualTo(CHECKSUMS.README_TXT);
    assertThat(Digester.digest(binDir.resolve("idea.bat"))).isEqualTo(CHECKSUMS.IDEA_BAT);
    assertThat(Digester.digest(libDir.resolve("annotations.jar"))).isEqualTo(CHECKSUMS.ANNOTATIONS_JAR);
    assertThat(Digester.digest(libDir.resolve("annotations_changed.jar"))).isEqualTo(CHECKSUMS.ANNOTATIONS_CHANGED_JAR);
    assertThat(Digester.digest(libDir.resolve("boot.jar"))).isEqualTo(CHECKSUMS.BOOT_JAR);
    assertThat(Digester.digest(libDir.resolve("boot_with_directory_becomes_file.jar"))).isEqualTo(CHECKSUMS.BOOT_CHANGED_JAR);
    assertThat(Digester.digest(libDir.resolve("bootstrap.jar"))).isEqualTo(CHECKSUMS.BOOTSTRAP_JAR);
    assertThat(Digester.digest(libDir.resolve("bootstrap_deleted.jar"))).isEqualTo(CHECKSUMS.BOOTSTRAP_DELETED_JAR);
  }

  @Test
  public void testHelpers() {
    assertThat(Digester.isFile(CHECKSUMS.README_TXT)).isTrue();
    assertThat(Digester.isFile(CHECKSUMS.ANNOTATIONS_JAR)).isTrue();
    assertThat(Digester.isFile(Digester.INVALID)).isFalse();
    assertThat(Digester.isFile(Digester.DIRECTORY)).isFalse();

    assertThat(Digester.isSymlink(CHECKSUMS.LINK_TO_README_TXT)).isTrue();
    assertThat(Digester.isSymlink(CHECKSUMS.README_TXT)).isFalse();
    assertThat(Digester.isSymlink(Digester.INVALID)).isFalse();
    assertThat(Digester.isSymlink(Digester.DIRECTORY)).isFalse();
  }

  @Test
  public void testSymlinks() throws Exception {
    IoTestUtil.assumeSymLinkCreationIsSupported();

    Path simpleLink = Files.createSymbolicLink(getTempFile("Readme.simple.link").toPath(), Path.of("Readme.txt"));
    Path relativeLink = Files.createSymbolicLink(getTempFile("Readme.relative.link").toPath(), Path.of("./Readme.txt"));
    Path absoluteLink = Files.createSymbolicLink(getTempFile("Readme.absolute.link").toPath(), dataDir.toPath().resolve("Readme.txt"));

    assertThat(Digester.digest(simpleLink)).isEqualTo(CHECKSUMS.LINK_TO_README_TXT);
    assertThat(Digester.digest(relativeLink)).isEqualTo(CHECKSUMS.LINK_TO_DOT_README_TXT);

    assertThatThrownBy(() -> Digester.digest(absoluteLink))
      .isInstanceOf(IOException.class)
      .hasMessageStartingWith("An absolute link");
  }

  @Test
  public void testExecutables() throws Exception {
    assumeFalse("Windows-allergic", Utils.IS_WINDOWS);

    Path testFile = Files.copy(dataDir.toPath().resolve("bin/idea.bat"), tempDir.getRoot().toPath().resolve("idea.bat"));
    assertThat(Digester.digest(testFile)).isEqualTo(CHECKSUMS.IDEA_BAT);
    NioFiles.setExecutable(testFile);
    assertThat(Digester.digest(testFile)).isEqualTo(CHECKSUMS.IDEA_BAT | Digester.EXECUTABLE);
  }
}
