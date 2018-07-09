package de.plushnikov.intellij.plugin;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.testFramework.InspectionTestCase;
import com.intellij.util.PathUtil;
import de.plushnikov.TestUtil;
import de.plushnikov.intellij.plugin.inspection.LombokInspection;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author Plushnikov Michail
 */
public class InspectionTest extends InspectionTestCase {

  @Override
  protected void setUp() throws Exception {
    VfsRootAccess.allowRootAccess(new File(getTestDataPath(), "lib").getCanonicalPath());
    super.setUp();
  }

  @Override
  protected String getTestDataPath() {
    return "testData/inspection";
  }

  private void doTest() {
    doTest(getTestName(true), new LombokInspection());
  }

  public void testIssue37() {
    doTest();
  }

  public void testDataEqualsAndHashCodeOverride() {
    doTest();
  }

  public void testEqualsAndHashCodeCallSuper() {
    doTest();
  }

  public void testEqualsAndHashCodeCallSuperConfigSkip() {
    doTest();
  }

  public void testValInspection() {
    doTest();
  }

  public void testSetterOnFinalField() {
    doTest();
  }

  @Override
  protected void setupRootModel(@NotNull String testDir, @NotNull VirtualFile[] sourceDir, String sdkName) {
    super.setupRootModel(testDir, sourceDir, sdkName);
    TestUtil.addLibrary(getTestRootDisposable(), getModule(), "Lombok", PathUtil.toSystemIndependentName(new File(getTestDataPath(), "lib").getAbsolutePath()), "lombok.jar");
  }
}
