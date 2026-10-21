// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.highlighting;

import com.intellij.codeInsight.XmlTestUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.BraceHighlightingTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public class XmlBraceHighlighterTest extends LightPlatformCodeInsightTestCase {
  private static final String BASE_PATH = "/codeInsight/braceHighlighter/";

  @Override
  protected @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }

  private void doTest() throws IOException {
    VirtualFile virtualFile = BraceHighlightingTestUtil.getTestFile(getTestName(true), BASE_PATH, getTestDataPath());
    String text = StringUtil.convertLineSeparators(VfsUtilCore.loadText(virtualFile));
    configureFromFileText(virtualFile.getName(), text);
    BraceHighlightingTestUtil.assertBraceMatched(getFile(), getEditor());
  }

  public void testXml() throws Exception { doTest(); }
  public void testXml_cdata() throws Exception { doTest(); }
  public void testHtml1() throws Exception { doTest(); }
  public void testHtml2() throws Exception { doTest(); }
  public void testHtml3() throws Exception { doTest(); }
}
