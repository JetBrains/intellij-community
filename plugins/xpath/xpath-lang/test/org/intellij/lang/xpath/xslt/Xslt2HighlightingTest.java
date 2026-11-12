// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath.xslt;

import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.TestDisposable;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import org.intellij.lang.xpath.XPathTestUtil;
import org.intellij.lang.xpath.xslt.impl.XsltStuffProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.sharedCodeInsightProjectFixture;

@TestApplication
public class Xslt2HighlightingTest {
  private static final TestFixture<Project> projectFixture = sharedCodeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = sharedCodeInsightFixture(projectFixture);
  private String testFileName;

  @BeforeEach
  void setUp(TestInfo testInfo, @TestDisposable Disposable disposable) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(XPathTestUtil.getTestDataPath("xslt2/highlighting"));
    testFileName = XPathTestUtil.getTestFileName(testInfo);
    myFixture.enableInspections(XsltStuffProvider.INSPECTION_CLASSES);
    List<String> list = new ArrayList<>();
    list.add("urn:my");
    list.add("nsx");
    ExternalResourceManagerEx.getInstanceEx().addIgnoredResources(list, disposable);
  }

  @Test
  public void testCurrentMode() {
    doXsltHighlighting();
  }

  @Test
  public void testXslt2Example() {
    doXsltHighlighting();
  }

  @Test
  public void testNonDuplicateVariable() {
    doXsltHighlighting();
  }

  @Test
  public void testTypeResolving() {
    doXsltHighlighting();
  }

  @Test
  public void testEscapedXPathString() {
    doXsltHighlighting();
  }

  @Test
  public void testWildcardNamespace() {
    doXsltHighlighting();
  }

  @Test
  public void testUnknownSchemaType() {
    doXsltHighlighting();
  }

  @Test
  public void testAttributeValueTemplateWithComment() {
    doXsltHighlighting();
  }

  @Test
  public void testSchemaTypeWithDashes() {
    doXsltHighlighting("move-def.xsd");
  }

  @Test
  public void testXslt3() {
    doXsltHighlighting();
  }

  @Test
  public void testNamespaces() {
    doXsltHighlighting();
  }

  private void doXsltHighlighting(String... moreFiles) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String name = testFileName;
    myFixture.testHighlighting(true, false, false, ArrayUtil.mergeArrays(new String[]{ name + ".xsl" }, moreFiles));
  }
}
