package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.codeInsight.XmlTestUtil;
import com.intellij.xml.util.CheckEmptyTagInspection;
import org.jetbrains.annotations.NotNull;

public class ReplaceTagEmptyEndWithEmptyBodyTest extends LightQuickFixParameterizedTestCase {
  @Override
  public @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new RequiredAttributesInspection(),
      new CheckEmptyTagInspection()
    };
  }
  

  @Override
  protected String getBasePath() {
    return "/quickFix/replaceTagEmptyEndWithEmptyBody";
  }

}
