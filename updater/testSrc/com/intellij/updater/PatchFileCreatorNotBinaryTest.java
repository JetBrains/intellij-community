/*
 * Copyright (C) 2014 The Android Open Source Project
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
import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PatchFileCreatorNotBinaryTest extends PatchFileCreatorTest {
  @Test
  public void failOnEmptyTargetJar() throws Exception {
    File sourceJar = new File(myOlderDir, "lib/empty.jar");
    FileUtil.copy(new File(myOlderDir, "lib/annotations.jar"), sourceJar);

    try {
      File targetJar = new File(myNewerDir, "lib/empty.jar");
      FileUtil.writeToFile(targetJar, "x");

      try {
        createPatch();
        fail("Should have failed to create a patch against empty .jar");
      }
      catch (IOException e) {
        assertEquals("Corrupted file: " + targetJar, e.getMessage());
      }
      finally {
        FileUtil.delete(targetJar);
      }
    }
    finally {
      FileUtil.delete(sourceJar);
    }
  }

  @Test
  public void failOnEmptySourceJar() throws Exception {
    File sourceJar = new File(myOlderDir, "lib/empty.jar");
    FileUtil.writeToFile(sourceJar, "x");

    try {
      File targetJar = new File(myNewerDir, "lib/empty.jar");
      FileUtil.copy(new File(myNewerDir, "lib/annotations.jar"), targetJar);

      try {
        createPatch();
        fail("Should have failed to create a patch from empty .jar");
      }
      catch (IOException e) {
        assertEquals("Corrupted file: " + sourceJar, e.getMessage());
      }
      finally {
        FileUtil.delete(targetJar);
      }
    }
    finally {
      FileUtil.delete(sourceJar);
    }
  }
}