/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.xpath.xslt;

import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ArrayUtil;
import org.intellij.lang.xpath.TestBase;
import org.intellij.lang.xpath.xslt.impl.XsltStuffProvider;

public class Xslt2HighlightingTest extends TestBase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(XsltStuffProvider.INSPECTION_CLASSES);
    ApplicationManager.getApplication().runWriteAction(() -> {
      ExternalResourceManagerEx.getInstanceEx().addIgnoredResource("urn:my");
      ExternalResourceManagerEx.getInstanceEx().addIgnoredResource("nsx");
    });
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
