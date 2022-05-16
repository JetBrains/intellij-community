package com.intellij.codeInsight.defaultAction;

import com.intellij.codeInsight.XmlTestUtil;
import org.jetbrains.annotations.NotNull;

public class XmlSlashTest extends DefaultActionTestCase {
  @Override
  protected @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }

  public void testXmlSlash() {
    String path = "/codeInsight/defaultAction/slash/";

    configureByFile(path + "1.xml");
    performAction('/');
    checkResultByFile(path + "1_after.xml");

    configureByFile(path + "2.xml");
    performAction('/');
    checkResultByFile(path + "2_after.xml");

    configureByFile(path + "3.xml");
    performAction('/');
    checkResultByFile(path + "3_after.xml");

    configureByFile(path + "4.xml");
    performAction('/');
    checkResultByFile(path + "4_after.xml");
  }

  public void testHtmlSlash() {
    String path = "/codeInsight/defaultAction/slash/";

    configureByFile(path + "1.html");
    performAction('/');
    checkResultByFile(path + "1_after.html");
  }
}
