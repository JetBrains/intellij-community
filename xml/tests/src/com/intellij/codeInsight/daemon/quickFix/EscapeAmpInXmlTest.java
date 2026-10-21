package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInsight.XmlTestUtil;
import com.intellij.xml.util.CheckValidXmlInScriptBodyInspection;
import org.jetbrains.annotations.NotNull;

public class EscapeAmpInXmlTest extends LightQuickFixParameterizedTestCase {
  @Override
  public @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }



  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new CheckValidXmlInScriptBodyInspection()
    };
  }
  @Override
  protected String getBasePath() {
    return "/quickFix/escapeAmp";
  }

}
