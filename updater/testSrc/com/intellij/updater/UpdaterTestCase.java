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
  };

  protected CheckSums CHECKSUMS;
  private TempDirTestFixture myTempDirFixture;

  @Before
  public void setUp() throws Exception {
    Runner.initLogger(System.getProperty("java.io.tmpdir"));
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
    public final long FOCUSKILLER_DLL;

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
    }
  }
}