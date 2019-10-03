/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.studio.updater;

import com.android.testutils.BazelRunfilesManifestProcessor;
import com.android.testutils.JarTestSuiteRunner;
import com.android.testutils.TestUtils;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.updater.PatchApplyingRevertingTest;
import java.io.File;
import java.io.IOException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

@RunWith(JarTestSuiteRunner.class)
@JarTestSuiteRunner.ExcludeClasses({
  PatchApplyingRevertingTest.class, // Abstract test
  UpdaterTestSuite.class, // Self
  StudioPatchUpdaterIntegrationTest.class, // Separate Bazel target
})
public class UpdaterTestSuite {

  private static File tempFolder;

  @BeforeClass
  public static void setUp() throws IOException {
    BazelRunfilesManifestProcessor.setUpRunfiles();
    tempFolder = FileUtil.createTempDirectory("UpdaterTestSuite", "d");
    // UpdaterTestCase.setUp() calls PathManagerEx.findFileUnderCommunityHome which uses PathManager which reads idea.home.path
    System.setProperty("idea.home.path", tempFolder.getAbsolutePath());
    File updaterTestData = new File(tempFolder, "updater/testData");
    // Copy as some of the updater utils have special handling of symlinks.
    FileUtil.copyDir(TestUtils.getWorkspaceFile("tools/idea/updater/testData"), updaterTestData);
  }

  @AfterClass
  public static void cleanup() {
    FileUtil.delete(tempFolder);
  }
}
