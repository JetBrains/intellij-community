package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.XmlTestUtil;
import com.intellij.testFramework.EditorActionTestCase;
import org.jetbrains.annotations.NotNull;

public class RemoveEnclosingTagTest extends EditorActionTestCase {
  @Override
  public @NotNull String getTestDataPath() {
    return XmlTestUtil.getXmlTestDataPath();
  }

  public void test1() { doTest(); }
  public void test2() { doTest(); }
  public void test3() { doTest(); }
  public void test4() {
    doTest("xhtml");
    doTest("html");
    doTest("xml");
  }
  public void test5() {
    doTest("xhtml");
    doTest("html");
  }

  private void doTest() {
    doTest("xml");
  }
  private void doTest(String ext) {
    String path = "/codeInsight/removeTag/";
    doFileTest(path + getTestName(false) + "." + ext, path + getTestName(false) + "_after." + ext, true);
  }

  @Override
  protected String getActionId() {
    return "Unwrap";
  }
}
