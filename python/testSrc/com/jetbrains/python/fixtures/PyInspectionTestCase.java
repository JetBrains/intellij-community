package com.jetbrains.python.fixtures;

import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.psi.PsiFile;
import com.jetbrains.python.inspections.PyInspection;
import org.jetbrains.annotations.NotNull;

/**
 * Helps you to create inspection tests.
 * <br/>
 * For each case
 * <ol>
 * <li>Create file {@code testData/inspections/_YOUR_INSPECTION_CLASS_SIMPLE_NAME_/CASE_NAME_CAMEL_CASE.py}</li>
 * <li>Create method {@code test_YOUR_CASE_NAME_PASCAL_CASE} that runs {@link #doTest()}</li>
 * <li>Overwrite {@link #isInfo()}, {@link #isWarning()} or {@link #isWeakWarning()} to configure what to check</li>
 * </ol>
 *
 * @author link
 */
public abstract class PyInspectionTestCase extends PyTestCase {

  @NotNull
  protected abstract Class<? extends PyInspection> getInspectionClass();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    InspectionProfileImpl.INIT_INSPECTIONS = true;
  }

  @Override
  protected void tearDown() throws Exception {
    InspectionProfileImpl.INIT_INSPECTIONS = false;
    super.tearDown();
  }

  /**
   * Launches test. To be called by test author
   */
  protected void doTest() {
    final PsiFile currentFile = myFixture.configureByFile(getTestFilePath());
    configureInspection();
    assertSdkRootsNotParsed(currentFile);
  }

  protected void doMultiFileTest() {
    doMultiFileTest("a.py");
  }

  protected void doMultiFileTest(@NotNull String filename) {
    myFixture.copyDirectoryToProject(getTestDirectoryPath(), "");
    final PsiFile currentFile = myFixture.configureFromTempProjectFile(filename);
    configureInspection();
    assertProjectFilesNotParsed(currentFile);
    assertSdkRootsNotParsed(currentFile);
  }

  protected void configureInspection() {
    myFixture.enableInspections(getInspectionClass());
    myFixture.checkHighlighting(isWarning(), isInfo(), isWeakWarning());
  }

  protected String getTestFilePath() {
    return getTestCaseDirectory() + getTestName(isLowerCaseTestFile()) + ".py";
  }

  protected String getTestDirectoryPath() {
    return getTestCaseDirectory() + getTestName(false);
  }

  protected String getTestCaseDirectory() {
    return "inspections/" + getInspectionClass().getSimpleName() + "/";
  }

  protected boolean isLowerCaseTestFile() {
    return true;
  }

  protected boolean isWeakWarning() {
    return true;
  }

  protected boolean isInfo() {
    return false;
  }

  protected boolean isWarning() {
    return true;
  }
}
