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

import junit.framework.TestCase;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class UtilsTest extends TestCase {

  public static boolean mIsWindows = System.getProperty("os.name").startsWith("Windows");

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  public void testDelete() throws Exception {
    File f = File.createTempFile("test", "tmp");
    assertTrue(f.exists());

    try {
      Utils.delete(f);
      assertFalse(f.exists());
    } finally {
      f.delete();
    }
  }

  public void testDelete_LockedFile() throws Exception {
    File f = File.createTempFile("test", "tmp");
    assertTrue(f.exists());

    long millis = 0;
    FileWriter fw = new FileWriter(f);
    try {
      // This locks the file on Windows, preventing it from being deleted.
      // Utils.delete() will retry for about 100 ms.
      fw.write("test");
      millis = System.currentTimeMillis();

      Utils.delete(f);

    } catch (IOException e) {
      millis = System.currentTimeMillis() - millis;
      assertEquals("Cannot delete file " + f.getAbsolutePath(), e.getMessage());
      assertTrue("Utils.delete took " + millis + " ms, which is less than the expected 100 ms.", millis >= 100);
      return;

    } finally {
      fw.close();
      f.delete();
    }

    assertFalse("Utils.delete did not fail with the expected IOException on Windows.", mIsWindows);
  }
}
