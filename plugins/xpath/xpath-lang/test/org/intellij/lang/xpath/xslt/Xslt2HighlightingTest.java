// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath.xslt;

import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.util.ArrayUtil;
import org.intellij.lang.xpath.TestBase;
import org.intellij.lang.xpath.xslt.impl.XsltStuffProvider;

import java.util.ArrayList;
import java.util.List;

public class Xslt2HighlightingTest extends TestBase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(XsltStuffProvider.INSPECTION_CLASSES);
    List<String> list = new ArrayList<>();
    list.add("urn:my");
    list.add("nsx");
    ExternalResourceManagerEx.getInstanceEx().addIgnoredResources(list, getTestRootDisposable());
  }

  public void testCurrentMode() {
    doXsltHighlighting();
  }

  public void testXslt2Example() {
    doXsltHighlighting();
  }

  public void testNonDuplicateVariable() {
    doXsltHighlighting();
  }

  public void testTypeResolving() {
    doXsltHighlighting();
  }

  public void testEscapedXPathString() {
    doXsltHighlighting();
  }

  public void testWildcardNamespace() {
    doXsltHighlighting();
  }

  public void testUnknownSchemaType() {
    doXsltHighlighting();
  }

  public void testAttributeValueTemplateWithComment() {
    doXsltHighlighting();
  }

  public void testSchemaTypeWithDashes() {
    doXsltHighlighting("move-def.xsd");
  }

  private void doXsltHighlighting(String... moreFiles) {
    final String name = getTestFileName();
    myFixture.testHighlighting(true, false, false, ArrayUtil.mergeArrays(new String[]{ name + ".xsl" }, moreFiles));
  }

  @Override
  protected String getSubPath() {
    return "xslt2/highlighting";
  }
}
