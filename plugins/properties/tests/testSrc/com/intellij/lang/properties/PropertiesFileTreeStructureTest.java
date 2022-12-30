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
    doTest("""
             a.b.c=dddd
             a.b.d=dddd
             a.x=i
             b.n.p=ooo""",

           """
             -Test.properties
              -a
               -b
                c
                d
               x
              b.n.p""");
  }

  public void testNesting() {
    doTest("""
             a=dddd
             a.b=dddd
             a.b.c=i
             a.b.c.d=ooo""",

           """
             -Test.properties
              -a
               <property>
               -b
                <property>
                -c
                 <property>
                 d""");
  }

  public void testGroupSort() {
    doTest("""
             log4j.category.x=dd
             log4j.category.xdo=dd
             log4j.category.middlegen.swing.ss=dd
             log4j.category.middlegen.plugins.ss=dd
             log4j.appender.middlegen.plugins.ss=dd""",

           """
             -Test.properties
              -log4j
               appender.middlegen.plugins.ss
               -category
                -middlegen
                 plugins.ss
                 swing.ss
                x
                xdo""");
  }

  public void testFunkyGroups() {
    doTest("""
             errors.byte={0} must be a BYTE type\s
             errors.short={0} must be a SHORT type\s
             error.date={0} must be a DATE types\s
             error.range={0} is not between {1} and {2}\s
             errors.email={0} is not a valid Email Address\s
             errors.ipaddress={0} is not a valid IP address""",

           """
             -Test.properties
              -error
               date
               range
              -errors
               byte
               email
               ipaddress
               short""");
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
