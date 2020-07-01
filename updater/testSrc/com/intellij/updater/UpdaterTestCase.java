// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.updater;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.rules.TempDirectory;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import java.io.File;

public abstract class UpdaterTestCase {
  protected static class TestUpdaterUI extends ConsoleUpdaterUI {
    public boolean cancelled = false;

    @Override public void setDescription(String oldBuildDesc, String newBuildDesc) { }
    @Override public void startProcess(String title) { }
    @Override public void checkCancelled() throws OperationCancelledException { if (cancelled) throw new OperationCancelledException(); }
    @Override public void showError(String message) { }
  }

  @Rule public TempDirectory tempDir = new TempDirectory();

  protected File dataDir;
  protected TestUpdaterUI TEST_UI;
  protected CheckSums CHECKSUMS;

  @Before
  public void before() throws Exception {
    dataDir = PathManagerEx.findFileUnderCommunityHome("updater/testData");

    Runner.checkCaseSensitivity(dataDir.getPath());
    Runner.initTestLogger();

    TEST_UI = new TestUpdaterUI();

    CHECKSUMS = new CheckSums(
      new File(dataDir, "Readme.txt").length() == 7132,
      File.separatorChar == '\\');
  }

  @After
  public void after() throws Exception {
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
    public final long LINK_TO_README_TXT = 2305843011042707672L;
    public final long LINK_TO_DOT_README_TXT;

    public CheckSums(boolean crLfs, boolean backwardSlashes) {
      README_TXT = crLfs ? 1272723667L : 7256327L;
      IDEA_BAT = crLfs ? 3088608749L : 1493936069L;
      LINK_TO_DOT_README_TXT = backwardSlashes ? 2305843011210142148L : 2305843009503057206L;
    }
  }
}