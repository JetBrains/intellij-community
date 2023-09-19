// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;

public class XmlXIncludeTest extends LightJavaCodeInsightFixtureTestCase {
  public void testBasicXi() {
    configure();
    assertSubtagNames("before", "include1", "after");
  }

  public void testXpointerXi1() {
    configure();
    assertSubtagNames("before", "foo", "bar", "after");
  }

  public void testXmlBase() {
    myFixture.copyDirectoryToProject("xmlBase", "xmlBase");
    myFixture.enableInspections(XmlPathReferenceInspection.class);
    myFixture.testHighlighting("xmlBase/XmlBase.xml");
  }

  @Override
  protected String getBasePath() {
    return "/xml/tests/testData/" + BASE_PATH;
  }

  private void assertSubtagNames(String... expectedNames) {
    final XmlTag tag = ((XmlFile)myFixture.getFile()).getDocument().getRootTag();
    final List<String> names = Arrays.stream(tag.getSubTags()).map(t -> t.getName()).toList();

    TestCase.assertEquals("subtags are different", StringUtil.join(expectedNames, "\n"), StringUtil.join(names, "\n"));
  }

  private void configure() {
    myFixture.configureByFiles(getName() + ".xml", "/include1.xml");
  }

  public void testModifyingIncludedFile() {
    myFixture.configureByText("a.xml", """
      <root xmlns:xi="http://www.w3.org/2001/XInclude">
        <before/>
        <xi:include href="include1.xml" xpointer="xpointer(/include1/*)"/>
        <after/>
      </root>
      """);
    VirtualFile inc1 = myFixture.copyFileToProject("include1.xml");
    assertSubtagNames("before", "foo", "bar", "after");

    changeText(inc1, "<include1><a/></include1>");

    assertSubtagNames("before", "a", "after");
  }

  public void testModifyingTransitivelyIncludedFile() {
    myFixture.configureByText("a.xml", """
      <root xmlns:xi="http://www.w3.org/2001/XInclude">
        <before/>
        <xi:include href="include1.xml" xpointer="xpointer(/include1/*)"/>
        <after/>
      </root>
      """);
    myFixture.addFileToProject("include1.xml", """
      <include1 xmlns:xi="http://www.w3.org/2001/XInclude">
        <foo attr1="val1"> <fooChild/> </foo>
        <bar/>
        <xi:include href="include2.xml"/>
      </include1>
      """);
    PsiFile inc2 = myFixture.addFileToProject("include2.xml", "<a/>");

    assertSubtagNames("before", "foo", "bar", "a", "after");

    changeText(inc2.getVirtualFile(), "<b/>");

    assertSubtagNames("before", "foo", "bar", "b", "after");
  }

  private void changeText(final VirtualFile inc1, final String s) {
    WriteAction.run(
      () -> {
        FileDocumentManager.getInstance().getDocument(inc1).setText(s);
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
      }
    );
  }

  private static final String BASE_PATH = "/psi/xi";
}
