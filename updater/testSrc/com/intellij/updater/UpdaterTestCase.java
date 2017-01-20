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

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.rules.TempDirectory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.io.File;

public abstract class UpdaterTestCase {
  protected static final UpdaterUI TEST_UI = new ConsoleUpdaterUI() {
    @Override public void startProcess(String title) { }
    @Override public void setStatus(String status) { }
    @Override public void setDescription(String oldBuildDesc, String newBuildDesc) { }
    @Override public boolean showWarning(String message) { return false; }
  };

  @Rule public TempDirectory tempDir = new TempDirectory();

  protected CheckSums CHECKSUMS;
  protected File dataDir;

  @Before
  public void setUp() throws Exception {
    dataDir = PathManagerEx.findFileUnderCommunityHome("updater/testData");

    Runner.checkCaseSensitivity(dataDir.getPath());
    Runner.initTestLogger();

    boolean windowsLineEnds = new File(dataDir, "Readme.txt").length() == 7132;
    CHECKSUMS = new CheckSums(windowsLineEnds);
  }

  @After
  public void tearDown() throws Exception {
    Utils.cleanup();
  }

  public File getTempFile(String fileName) {
    return new File(tempDir.getRoot(), fileName);
  }

  protected static class CheckSums {
    public final long README_TXT;
    public final long IDEA_BAT;
    public final long ANNOTATIONS_JAR;
    public final long BOOTSTRAP_JAR;
    public final long BOOTSTRAP_JAR_BINARY;
    public final long FOCUS_KILLER_DLL;
    public final long ANNOTATIONS_JAR_NORM;
    public final long ANNOTATIONS_CHANGED_JAR_NORM;
    public final long BOOT_JAR_NORM;
    public final long BOOT2_JAR_NORM;
    public final long BOOT2_CHANGED_WITH_UNCHANGED_CONTENT_JAR_NORM;
    public final long BOOT_WITH_DIRECTORY_BECOMES_FILE_JAR_NORM;
    public final long BOOTSTRAP_JAR_NORM;
    public final long BOOTSTRAP_DELETED_JAR_NORM;
    public final long LINK_TO_README_TXT;
    public final long LINK_TO_DOT_README_TXT;

    public CheckSums(boolean windowsLineEnds) {
      README_TXT = windowsLineEnds ? 1272723667L : 7256327L;
      IDEA_BAT = windowsLineEnds ? 3088608749L : 1493936069L;
      ANNOTATIONS_JAR = 2119442657L;
      BOOTSTRAP_JAR = 2082851308L;
      BOOTSTRAP_JAR_BINARY = 2745721972L;
      FOCUS_KILLER_DLL = 1991212227L;
      ANNOTATIONS_JAR_NORM = 2119442657L;
      ANNOTATIONS_CHANGED_JAR_NORM = 4088078858L;
      BOOT_JAR_NORM = 3018038682L;
      BOOT2_JAR_NORM = 2406818996L;
      BOOT2_CHANGED_WITH_UNCHANGED_CONTENT_JAR_NORM = 2406818996L;
      BOOT_WITH_DIRECTORY_BECOMES_FILE_JAR_NORM = 1972168924;
      BOOTSTRAP_JAR_NORM = 2082851308;
      BOOTSTRAP_DELETED_JAR_NORM = 544883981L;
      LINK_TO_README_TXT = 2305843011042707672L;
      LINK_TO_DOT_README_TXT = 2305843009503057206L;
    }
  }
}