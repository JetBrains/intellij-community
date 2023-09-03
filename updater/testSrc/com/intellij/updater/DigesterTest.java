// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.updater;

import com.intellij.openapi.util.io.IoTestUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;

public class DigesterTest extends UpdaterTestCase {
  @Test
  public void testBasics() throws Exception {
    File binDir = new File(dataDir, "bin"), libDir = new File(dataDir, "lib");

    assertEquals(Digester.DIRECTORY, Digester.digestRegularFile(binDir));
    assertEquals(Digester.DIRECTORY, Digester.digestRegularFile(libDir));

    assertEquals(CHECKSUMS.README_TXT, Digester.digestRegularFile(new File(dataDir, "Readme.txt")));
    assertEquals(CHECKSUMS.BOOTSTRAP_JAR, Digester.digestRegularFile(new File(libDir, "bootstrap.jar")));
    assertEquals(CHECKSUMS.ANNOTATIONS_JAR, Digester.digestRegularFile(new File(libDir, "annotations.jar")));
    assertEquals(CHECKSUMS.ANNOTATIONS_CHANGED_JAR, Digester.digestRegularFile(new File(libDir, "annotations_changed.jar")));
    assertEquals(CHECKSUMS.BOOT_JAR, Digester.digestRegularFile(new File(libDir, "boot.jar")));
    assertEquals(CHECKSUMS.BOOT_CHANGED_JAR, Digester.digestRegularFile(new File(libDir, "boot_with_directory_becomes_file.jar")));
    assertEquals(CHECKSUMS.BOOTSTRAP_JAR, Digester.digestRegularFile(new File(libDir, "bootstrap.jar")));
    assertEquals(CHECKSUMS.BOOTSTRAP_DELETED_JAR, Digester.digestRegularFile(new File(libDir, "bootstrap_deleted.jar")));
  }

  @Test
  public void testHelpers() {
    assertTrue(Digester.isFile(CHECKSUMS.README_TXT));
    assertTrue(Digester.isFile(CHECKSUMS.ANNOTATIONS_JAR));
    assertFalse(Digester.isFile(Digester.INVALID));
    assertFalse(Digester.isFile(Digester.DIRECTORY));

    assertTrue(Digester.isSymlink(CHECKSUMS.LINK_TO_README_TXT));
    assertFalse(Digester.isSymlink(CHECKSUMS.README_TXT));
    assertFalse(Digester.isSymlink(Digester.INVALID));
    assertFalse(Digester.isSymlink(Digester.DIRECTORY));
  }

  @Test
  public void testSymlinks() throws Exception {
    IoTestUtil.assumeSymLinkCreationIsSupported();

    File simpleLink = getTempFile("Readme.simple.link");
    @NotNull Path link2 = simpleLink.toPath();
    @NotNull Path target2 = Paths.get("Readme.txt");
    Files.createSymbolicLink(link2, target2);
    File relativeLink = getTempFile("Readme.relative.link");
    @NotNull Path link1 = relativeLink.toPath();
    @NotNull Path target1 = Paths.get("./Readme.txt");
    Files.createSymbolicLink(link1, target1);
    File absoluteLink = getTempFile("Readme.absolute.link");
    @NotNull Path link = absoluteLink.toPath();
    @NotNull Path target = Paths.get(dataDir.getPath() + "/Readme.txt");
    Files.createSymbolicLink(link, target);

    assertEquals(CHECKSUMS.LINK_TO_README_TXT, Digester.digestRegularFile(simpleLink));
    assertEquals(CHECKSUMS.LINK_TO_DOT_README_TXT, Digester.digestRegularFile(relativeLink));

    try {
      Digester.digestRegularFile(absoluteLink);
      fail("Absolute links should cause indigestion");
    }
    catch (IOException e) {
      assertThat(e.getMessage()).startsWith("An absolute link");
    }
  }

  @Test
  public void testExecutables() throws Exception {
    assumeFalse("Windows-allergic", Utils.IS_WINDOWS);

    File testFile = new File(tempDir.getRoot(), "idea.bat");
    Utils.copy(new File(dataDir, "bin/idea.bat"), testFile, false);
    assertEquals(CHECKSUMS.IDEA_BAT, Digester.digestRegularFile(testFile));
    Utils.setExecutable(testFile);
    assertEquals(CHECKSUMS.IDEA_BAT | Digester.EXECUTABLE, Digester.digestRegularFile(testFile));
  }
}
