// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.javaee.ExternalResourceManagerExImpl;
import com.intellij.psi.PsiReference;

/**
 * @author Maxim.Mossienko
 */
public class XmlRenameTest extends XmlRenameTestCase {

  public void test1() throws Exception {
    doTest("anothertag","xml");
  }
  public void test2() throws Exception {
    doTest("anothertag","xml");
  }
  public void test5() throws Exception {
    doTest("mine","xml");
  }

  public void test6() throws Exception {
    final String testName = getTestName(false);
    doTest("id2",new String[] {testName+ ".dtd",testName+".xml"},testName + "_after.dtd");
    final PsiReference[] references = findReferencesToReferencedElementAtCaret();
    assertEquals(2,references.length);
  }

  public void test7() throws Exception {
    final String testName = getTestName(false);
    doTest("my-event",new String[] {testName+ ".xsd",testName+".xml"},testName + "_after.xsd");
    final PsiReference[] references = findReferencesToReferencedElementAtCaret();
    assertEquals(2,references.length);
  }

  public void test8() throws Exception {
    final String testName = getTestName(false);
    doTest("my-id",new String[] {testName+ ".dtd",testName+".xml"},testName + "_after.dtd");
    final PsiReference[] references = findReferencesToReferencedElementAtCaret();
    assertEquals(1,references.length);
  }

  public void test9() throws Exception {
    final String testName = getTestName(false);
    doTest("9_2.dtd",new String[] {testName+".xml", testName+ ".dtd"},testName + "_after.xml");
    final PsiReference[] references = findReferencesToReferencedElementAtCaret();
    assertEquals(1,references.length);
  }

  public void test3() throws Exception {
    String testName = getTestName(false);
    doTest("span",new String[] {testName+ ".html","html.dtd"},testName + "_after.html");
    doTest("another-tag","xml");
  }

  public void testSchemaRename() throws Exception {
    String location = "SchemaRename.xsd";
    String url = "http://aaa.bbb";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, location, getTestRootDisposable());

    doTest("New-Company",new String[] {"SchemaRename.xml",location},"SchemaRename_after.xml");

    checkDescriptorRenamed();

    doTest("New-Company",new String[] {"SchemaRename_2.xml",location},"SchemaRename_2_after.xml");
    checkDescriptorRenamed();

  }

  public void testSchemaRename2() throws Exception {
    String location = "SchemaRename2.xsd";
    String url = "http://aaa.bbb";
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, location, getTestRootDisposable());

    doTest(
    "NewCompany",
    new String[] {location,"SchemaRename2.xml","SchemaRename2_2.xml"},
      "SchemaRename2_after.xsd",
    () -> assertEquals(7, findReferencesToReferencedElementAtCaret().length)
    );

    assertEquals(7, findReferencesToReferencedElementAtCaret().length);
  }

  public void testSchemaRenameForElementAnotherNamespace() throws Exception {
    doTest("NewCompany",new String[] {"SchemaRename3.xsd","SchemaRename3_2.xsd"},"SchemaRename3_after.xsd");
    final PsiReference[] references = findReferencesToReferencedElementAtCaret();
    assertEquals(3,references.length);
  }

  public void testSchemaRenameForComplexType() throws Exception {
    doTest("NewCompany2",new String[] {"SchemaRename4.xsd"},"SchemaRename4_after.xsd");
    final PsiReference[] references = findReferencesToReferencedElementAtCaret();
    assertEquals(3,references.length);
  }

  public void testRenameDtdUsedViaEntity() throws Exception {
    final String testName = getTestName(false);
    doTest("anothertag",new String[] {testName+".dtd",testName + ".xml"},testName + "_after.dtd");
    final PsiReference[] references = findReferencesToReferencedElementAtCaret();
    assertEquals(6,references.length);
  }
  
  public void testRenameNamespacePrefix() throws Exception {
    doTest("xxx", "xsd");
  }

  public void testRenameInsideCDATA() throws Exception {
    configureByFiles("insideCDATA", "insideCDATA/RenameInsideCDATA.xml");
    performAction("Shorten");
    checkResultByFile("insideCDATA/RenameInsideCDATA_after.xml");
  }
}
