package org.jetbrains.jps.uiDesigner.build;

import com.intellij.platform.bazel.runfiles.BazelLabel;
import com.intellij.testFramework.common.BazelTestUtil;
import org.jetbrains.ether.IncrementalTestCase;

import java.io.File;

public class FormsIncrementalBuildingTest extends IncrementalTestCase {
  public FormsIncrementalBuildingTest() {
    super("uiDesigner");
  }

  public void testSimple() {
    doTest().assertSuccessful();
  }

  @Override
  protected File getTestDataDirectory() {
    if (BazelTestUtil.isUnderBazelTest()) {
      var label = BazelLabel.Companion.fromString("@community//java/java-tests:testData");
      return BazelTestUtil.getFileFromBazelRuntime(label).resolve("compileServer/incremental/uiDesigner/" + getProjectName()).toFile();
    }
    return super.getTestDataDirectory();
  }
}
