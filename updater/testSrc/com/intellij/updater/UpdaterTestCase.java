package com.intellij.updater;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import org.junit.After;
import org.junit.Before;

import java.io.File;

public abstract class UpdaterTestCase {
  protected static final UpdaterUI TEST_UI = new ConsoleUpdaterUI(){
    @Override
    public void startProcess(String title) {
    }

    @Override
    public void setStatus(String status) {
    }

    @Override
    public void setDescription(String oldBuildDesc, String newBuildDesc) {
    }

    @Override
    public boolean showWarning(String message) {
      return false;
    }
  };

  protected CheckSums CHECKSUMS;
  private TempDirTestFixture myTempDirFixture;

  @Before
  public void setUp() throws Exception {
    Runner.initLogger();
    myTempDirFixture = IdeaTestFixtureFactory.getFixtureFactory().createTempDirTestFixture();
    myTempDirFixture.setUp();

    FileUtil.copyDir(PathManagerEx.findFileUnderCommunityHome("updater/testData"), getDataDir());

    boolean windowsLineEnds = new File(getDataDir(), "Readme.txt").length() == 7132;
    CHECKSUMS = new CheckSums(windowsLineEnds);
  }

  @After
  public void tearDown() throws Exception {
    myTempDirFixture.tearDown();
    Utils.cleanup();
  }

  public File getDataDir() {
    return getTempFile("data");
  }

  public File getTempFile(String fileName) {
    return new File(myTempDirFixture.getTempDirPath(), fileName);
  }

  protected static class CheckSums {
    public final long README_TXT;
    public final long IDEA_BAT;
    public final long ANNOTATIONS_JAR;
    public final long BOOTSTRAP_JAR;
    public final long BOOTSTRAP_JAR_BINARY;
    public final long FOCUSKILLER_DLL;
    public final long ANNOTATIONS_JAR_NORM;
    public final long ANNOTATIONS_CHANGED_JAR_NORM;
    public final long BOOT_JAR_NORM;
    public final long BOOT2_JAR_NORM;
    public final long BOOT2_CHANGED_WITH_UNCHANGED_CONTENT_JAR_NORM;
    public final long BOOT_WITH_DIRECTORY_BECOMES_FILE_JAR_NORM;
    public final long BOOTSTRAP_JAR_NORM;
    public final long BOOTSTRAP_DELETED_JAR_NORM;

    public CheckSums(boolean windowsLineEnds) {
      if (windowsLineEnds) {
        README_TXT = 1272723667L;
        IDEA_BAT = 3088608749L;
      }
      else {
        README_TXT = 7256327L;
        IDEA_BAT = 1493936069L;
      }
      ANNOTATIONS_JAR = 2119442657L;
      BOOTSTRAP_JAR = 2082851308L;
      FOCUSKILLER_DLL = 1991212227L;
      BOOTSTRAP_JAR_BINARY = 2745721972L;

      ANNOTATIONS_JAR_NORM = 2119442657L;
      ANNOTATIONS_CHANGED_JAR_NORM = 4088078858L;
      BOOT_JAR_NORM = 3018038682L;
      BOOT2_JAR_NORM = 2406818996L;
      BOOT2_CHANGED_WITH_UNCHANGED_CONTENT_JAR_NORM = 2406818996L;
      BOOT_WITH_DIRECTORY_BECOMES_FILE_JAR_NORM = 1972168924;
      BOOTSTRAP_JAR_NORM = 2082851308;
      BOOTSTRAP_DELETED_JAR_NORM = 544883981L;
    }
  }
}