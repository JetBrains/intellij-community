package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.impl.quickfix.FetchExtResourceAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 03.09.2003
 * Time: 19:32:51
 * To change this template use Options | File Templates.
 */
public class FetchExternalResourcesFixTest extends LightQuickFixParameterizedTestCase {
  public void test() throws Exception { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/quickFix/fetchExternalResources";
  }

  // just check for action availability
  @Override
  protected void doAction(String text, boolean actionShouldBeAvailable, String testFullPath, String testName) throws Exception {
    IntentionAction action = findActionWithText(text);
    if (action == null && actionShouldBeAvailable) {
      fail("Action with text '" + text + "' is not available in test " + testFullPath);
    }

    if (actionShouldBeAvailable && testName.equals("5.xml")) {
      final String uri = FetchExtResourceAction.findUri(myFile, myEditor.getCaretModel().getOffset());
      final String url = FetchExtResourceAction.findUrl(myFile, myEditor.getCaretModel().getOffset(),uri);
      assertEquals("http://www.springframework.org/schema/aop/spring-aop.xsd",url);
    }
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";
  }
}
