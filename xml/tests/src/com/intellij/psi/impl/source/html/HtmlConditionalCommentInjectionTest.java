package com.intellij.psi.impl.source.html;

import com.intellij.codeInsight.completion.CompletionTestCase;
import com.intellij.testFramework.PlatformTestUtil;

import java.io.File;

/**
 * @author spleaner
 */
@SuppressWarnings({"ALL"})
public class HtmlConditionalCommentInjectionTest extends CompletionTestCase {

  public void testHtmlCompletion() throws Exception {
    configureByFile("complete.html");
    checkResultByFile("complete_after.html");
  }

  public void testXhtmlCompletion() throws Exception {
    configureByFile("complete.xhtml");
    checkResultByFile("complete_after.xhtml");
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/commentInjection/";
  }
}
