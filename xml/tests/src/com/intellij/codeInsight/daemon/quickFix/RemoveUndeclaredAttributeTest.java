/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.htmlInspections.HtmlUnknownAttributeInspection;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.codeInsight.XmlTestUtil;
import org.jetbrains.annotations.NotNull;

public class RemoveUndeclaredAttributeTest extends LightQuickFixParameterizedTestCase {
  @Override
  public @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }


  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new RequiredAttributesInspection(),
      new HtmlUnknownAttributeInspection()
    };
  }


  @Override
  protected String getBasePath() {
    return "/quickFix/removeUndeclaredAttribute";
  }
}
