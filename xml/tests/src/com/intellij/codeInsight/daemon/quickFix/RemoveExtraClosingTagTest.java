// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.XmlTestUtil;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.htmlInspections.HtmlExtraClosingTagInspection;
import org.jetbrains.annotations.NotNull;

public class RemoveExtraClosingTagTest extends LightQuickFixParameterizedTestCase {


  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {new HtmlExtraClosingTagInspection()};
  }


  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/removeExtraClosingTag";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }
}
