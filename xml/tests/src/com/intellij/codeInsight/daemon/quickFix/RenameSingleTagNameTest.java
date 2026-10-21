package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.codeInsight.XmlTestUtil;
import org.jetbrains.annotations.NotNull;

public class RenameSingleTagNameTest extends LightQuickFixParameterizedTestCase {
  @Override
  public @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new RequiredAttributesInspection()
    };
  }
  

  @Override
  protected String getBasePath() {
    return "/quickFix/renameSingleTagName";
  }

}
