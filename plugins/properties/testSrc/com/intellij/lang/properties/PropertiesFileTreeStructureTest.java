// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties;

import com.intellij.ide.util.treeView.smartTree.Sorter;
import com.intellij.lang.properties.structureView.GroupByWordPrefixes;
import com.intellij.lang.properties.structureView.PropertiesFileStructureViewModel;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import javax.swing.*;

public class PropertiesFileTreeStructureTest extends BasePlatformTestCase {
  public void testGrouping() {
    doTest("a.b.c=dddd\n" +
           "a.b.d=dddd\n" +
           "a.x=i\n" +
           "b.n.p=ooo",

           "-Test.properties\n" +
           " -a\n" +
           "  -b\n" +
           "   c\n" +
           "   d\n" +
           "  x\n" +
           " b.n.p");
  }

  public void testNesting() {
    doTest("a=dddd\n" +
           "a.b=dddd\n" +
           "a.b.c=i\n" +
           "a.b.c.d=ooo",

           "-Test.properties\n" +
           " -a\n" +
           "  <property>\n" +
           "  -b\n" +
           "   <property>\n" +
           "   -c\n" +
           "    <property>\n" +
           "    d");
  }

  public void testGroupSort() {
    doTest("log4j.category.x=dd\n" +
           "log4j.category.xdo=dd\n" +
           "log4j.category.middlegen.swing.ss=dd\n" +
           "log4j.category.middlegen.plugins.ss=dd\n" +
           "log4j.appender.middlegen.plugins.ss=dd",

           "-Test.properties\n" +
           " -log4j\n" +
           "  appender.middlegen.plugins.ss\n" +
           "  -category\n" +
           "   -middlegen\n" +
           "    plugins.ss\n" +
           "    swing.ss\n" +
           "   x\n" +
           "   xdo");
  }

  public void testFunkyGroups() {
    doTest("errors.byte={0} must be a BYTE type \n" +
           "errors.short={0} must be a SHORT type \n" +
           "error.date={0} must be a DATE types \n" +
           "error.range={0} is not between {1} and {2} \n" +
           "errors.email={0} is not a valid Email Address \n" +
           "errors.ipaddress={0} is not a valid IP address",

           "-Test.properties\n" +
           " -error\n" +
           "  date\n" +
           "  range\n" +
           " -errors\n" +
           "  byte\n" +
           "  email\n" +
           "  ipaddress\n" +
           "  short");
  }

  private void doTest(String classText, String expected) {
    myFixture.configureByText("Test.properties", classText);
    myFixture.testStructureView(svc -> {
      svc.setActionActive(Sorter.ALPHA_SORTER_ID, true);
      svc.setActionActive(GroupByWordPrefixes.ID, true);
      svc.setActionActive(PropertiesFileStructureViewModel.KIND_SORTER_ID, true);
      JTree tree = svc.getTree();
      PlatformTestUtil.expandAll(tree);
      PlatformTestUtil.assertTreeEqual(tree, expected);
    });
  }
}
