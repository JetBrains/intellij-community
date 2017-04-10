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

  @SuppressWarnings("FieldMayBeStatic")
  protected static class CheckSums {
    public final long README_TXT;
    public final long IDEA_BAT;
    public final long ANNOTATIONS_JAR = 2119442657L;
    public final long ANNOTATIONS_JAR_BIN = 2525796836L;
    public final long ANNOTATIONS_CHANGED_JAR = 4088078858L;
    public final long ANNOTATIONS_CHANGED_JAR_BIN = 2587736223L;
    public final long BOOT_JAR = 3018038682L;
    public final long BOOT_WITH_DIRECTORY_BECOMES_FILE_JAR = 1972168924;
    public final long BOOT2_JAR = 2406818996L;
    public final long BOOT2_CHANGED_WITH_UNCHANGED_CONTENT_JAR = 2406818996L;
    public final long BOOTSTRAP_JAR = 2082851308L;
    public final long BOOTSTRAP_JAR_BIN = 2745721972L;
    public final long BOOTSTRAP_DELETED_JAR = 544883981L;
    public final long FOCUS_KILLER_DLL = 1991212227L;
    public final long LINK_TO_README_TXT = 2305843011042707672L;
    public final long LINK_TO_DOT_README_TXT = 2305843009503057206L;

    public CheckSums(boolean windowsLineEnds) {
      README_TXT = windowsLineEnds ? 1272723667L : 7256327L;
      IDEA_BAT = windowsLineEnds ? 3088608749L : 1493936069L;
    }
  }
}