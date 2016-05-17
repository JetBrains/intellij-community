/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import static org.junit.Assert.assertEquals;

public class DigesterTest extends UpdaterTestCase {
  @Test
  public void testBasics() throws Exception {
    assertEquals(CHECKSUMS.README_TXT, Digester.digestRegularFile(new File(getDataDir(), "Readme.txt"), false));
    assertEquals(CHECKSUMS.FOCUS_KILLER_DLL, Digester.digestRegularFile(new File(getDataDir(), "/bin/focuskiller.dll"), false));
    assertEquals(CHECKSUMS.BOOTSTRAP_JAR, Digester.digestZipFile(new File(getDataDir(), "/lib/bootstrap.jar")));
    assertEquals(CHECKSUMS.BOOTSTRAP_JAR_BINARY, Digester.digestRegularFile(new File(getDataDir(), "/lib/bootstrap.jar"), false));

    assertEquals(CHECKSUMS.ANNOTATIONS_JAR_NORM,
                 Digester.digestRegularFile(new File(getDataDir(), "/lib/annotations.jar"), true));
    assertEquals(CHECKSUMS.ANNOTATIONS_CHANGED_JAR_NORM,
                 Digester.digestRegularFile(new File(getDataDir(), "/lib/annotations_changed.jar"), true));
    assertEquals(CHECKSUMS.BOOT_JAR_NORM,
                 Digester.digestRegularFile(new File(getDataDir(), "/lib/boot.jar"), true));
    assertEquals(CHECKSUMS.BOOT2_JAR_NORM,
                 Digester.digestRegularFile(new File(getDataDir(), "/lib/boot2.jar"), true));
    assertEquals(CHECKSUMS.BOOT2_CHANGED_WITH_UNCHANGED_CONTENT_JAR_NORM,
                 Digester.digestRegularFile(new File(getDataDir(), "/lib/boot2_changed_with_unchanged_content.jar"), true));
    assertEquals(CHECKSUMS.BOOT_WITH_DIRECTORY_BECOMES_FILE_JAR_NORM,
                 Digester.digestRegularFile(new File(getDataDir(), "/lib/boot_with_directory_becomes_file.jar"), true));
    assertEquals(CHECKSUMS.BOOTSTRAP_JAR_NORM,
                 Digester.digestRegularFile(new File(getDataDir(), "/lib/bootstrap.jar"), true));
    assertEquals(CHECKSUMS.BOOTSTRAP_DELETED_JAR_NORM,
                 Digester.digestRegularFile(new File(getDataDir(), "/lib/bootstrap_deleted.jar"), true));
  }
}