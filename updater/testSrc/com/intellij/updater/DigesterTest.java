/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.updater;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class DigesterTest extends UpdaterTestCase {
  @Test
  public void testBasics() throws Exception {
    File binDir = new File(dataDir, "bin"), libDir = new File(dataDir, "lib");

    assertEquals(Digester.DIRECTORY, Digester.digestRegularFile(binDir, false));
    assertEquals(Digester.DIRECTORY, Digester.digestRegularFile(libDir, true));

    assertEquals(CHECKSUMS.README_TXT, Digester.digestRegularFile(new File(dataDir, "Readme.txt"), false));
    assertEquals(CHECKSUMS.FOCUS_KILLER_DLL, Digester.digestRegularFile(new File(binDir, "focuskiller.dll"), false));
    assertEquals(CHECKSUMS.BOOTSTRAP_JAR_BIN, Digester.digestRegularFile(new File(libDir, "bootstrap.jar"), false));
    assertEquals(CHECKSUMS.ANNOTATIONS_JAR, Digester.digestRegularFile(new File(libDir, "annotations.jar"), true));
    assertEquals(CHECKSUMS.ANNOTATIONS_CHANGED_JAR, Digester.digestRegularFile(new File(libDir, "annotations_changed.jar"), true));
    assertEquals(CHECKSUMS.BOOT_JAR, Digester.digestRegularFile(new File(libDir, "boot.jar"), true));
    assertEquals(CHECKSUMS.BOOT2_JAR, Digester.digestRegularFile(new File(libDir, "boot2.jar"), true));
    assertEquals(CHECKSUMS.BOOT2_CHANGED_WITH_UNCHANGED_CONTENT_JAR, Digester.digestRegularFile(new File(libDir, "boot2_changed_with_unchanged_content.jar"), true));
    assertEquals(CHECKSUMS.BOOT_WITH_DIRECTORY_BECOMES_FILE_JAR, Digester.digestRegularFile(new File(libDir, "boot_with_directory_becomes_file.jar"), true));
    assertEquals(CHECKSUMS.BOOTSTRAP_JAR, Digester.digestRegularFile(new File(libDir, "bootstrap.jar"), true));
    assertEquals(CHECKSUMS.BOOTSTRAP_DELETED_JAR, Digester.digestRegularFile(new File(libDir, "bootstrap_deleted.jar"), true));

    assertEquals(CHECKSUMS.BOOTSTRAP_JAR, Digester.digestZipFile(new File(libDir, "bootstrap.jar")));
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
    assumeTrue(!UtilsTest.IS_WINDOWS);

    File simpleLink = getTempFile("Readme.simple.link");
    Utils.createLink("Readme.txt", simpleLink);
    File relativeLink = getTempFile("Readme.relative.link");
    Utils.createLink("./Readme.txt", relativeLink);
    File absoluteLink = getTempFile("Readme.absolute.link");
    Utils.createLink(dataDir.getPath() + "/Readme.txt", absoluteLink);

    assertEquals(CHECKSUMS.LINK_TO_README_TXT, Digester.digestRegularFile(simpleLink, false));
    assertEquals(CHECKSUMS.LINK_TO_DOT_README_TXT, Digester.digestRegularFile(relativeLink, false));

    try {
      Digester.digestRegularFile(absoluteLink, false);
      fail("Absolute links should cause indigestion");
    }
    catch (IOException e) {
      assertThat(e.getMessage()).startsWith("Absolute link");
    }
  }
}