package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author spleaner
 */
public class XmlWrongClosingTagNameInspectionTest extends LightQuickFixParameterizedTestCase {
  public void test() { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/wrongClosingTagName";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/inspections";
  }
}
