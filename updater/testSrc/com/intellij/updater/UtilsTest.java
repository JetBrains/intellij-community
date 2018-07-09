/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.updater;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.rules.TempDirectory;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

public class UtilsTest {
  public static final boolean IS_WINDOWS = System.getProperty("os.name").startsWith("Windows");

  @Rule public TempDirectory tempDir = new TempDirectory();

  @BeforeClass
  public static void initLogger() {
    Runner.initTestLogger();
  }

  @Test
  public void testDelete() throws Exception {
    File f = tempDir.newFile("temp_file");
    assertTrue(f.exists());

    Utils.delete(f);
    assertFalse(f.exists());
  }

  @Test
  public void testDeleteReadonlyFile() throws Exception {
    File f = tempDir.newFile("temp_dir/temp_file");
    assertTrue(f.setWritable(false, false));
    File d = f.getParentFile();
    assertTrue(d.exists());

    Utils.delete(d);
    assertFalse(d.exists());
  }

  @Test
  public void testDeleteLockedFileOnWindows() throws Exception {
    assumeTrue(IS_WINDOWS);

    File f = tempDir.newFile("temp_file");
    assertTrue(f.exists());

    long ts = 0;
    try (FileWriter fw = new FileWriter(f)) {
      // This locks the file on Windows, preventing it from being deleted.
      // Utils.delete() will retry for about 100 ms.
      fw.write("test");
      ts = System.nanoTime();

      Utils.delete(f);
      fail("Utils.delete did not fail with the expected IOException on Windows");
    }
    catch (IOException e) {
      ts = (System.nanoTime() - ts) / 1_000_000;
      assertEquals("Cannot delete: " + f.getAbsolutePath(), e.getMessage());
      assertThat(ts).as("Utils.delete took " + ts + " ms, which is less than expected").isGreaterThanOrEqualTo(95);
    }
  }

  @Test
  public void testDeleteLockedFileOnUnix() throws Exception {
    assumeTrue(!IS_WINDOWS);

    File f = tempDir.newFile("temp_file");
    assertTrue(f.exists());

    try (FileWriter fw = new FileWriter(f)) {
      fw.write("test");
      Utils.delete(f);
    }
  }

  @Test
  public void testRecursiveDelete() throws Exception {
    File topDir = tempDir.newFolder("temp_dir");
    for (int i = 0; i < 3; i++) {
      for (int j = 0; j < 3; j++) {
        File file = new File(topDir, "dir" + i + "/file" + j);
        FileUtil.writeToFile(file, "test");
        assertTrue(file.exists());
      }
    }

    Utils.delete(topDir);
    assertFalse(topDir.exists());
  }

  @Test
  public void testNonRecursiveSymlinkDelete() throws Exception {
    assumeTrue(!IS_WINDOWS);

    File dir = tempDir.newFolder("temp_dir");
    File file = new File(dir, "file");
    FileUtil.writeToFile(file, "test");
    assertThat(dir.listFiles()).containsExactly(file);

    File link = new File(tempDir.getRoot(), "link");
    Utils.createLink(dir.getName(), link);
    assertTrue(Utils.isLink(link));
    assertThat(link.listFiles()).hasSize(1);

    Utils.delete(link);
    assertFalse(link.exists());
    assertThat(dir.listFiles()).containsExactly(file);
  }

  @Test
  public void testDeleteDanglingSymlink() throws Exception {
    assumeTrue(!IS_WINDOWS);

    File dir = tempDir.newFolder("temp_dir");
    File link = new File(dir, "link");
    Utils.createLink("dangling", link);
    assertThat(dir.listFiles()).containsExactly(link);

    Utils.delete(link);
    assertThat(dir.listFiles()).isEmpty();
  }
}