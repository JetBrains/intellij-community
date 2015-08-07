/*
 * Copyright 2007 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.intellij.plugins.relaxNG;

import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.javaee.ExternalResourceManagerExImpl;
import com.intellij.openapi.application.ApplicationManager;
import org.intellij.plugins.testUtil.CopyFile;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 25.07.2007
 */
public class RngXmlHighlightingTest extends HighlightingTestBase {

  @Override
  public String getTestDataPath() {
    return "highlighting";
  }

  @Override
  protected void init() {
    super.init();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final ExternalResourceManagerEx m = ExternalResourceManagerEx.getInstanceEx();
        ExternalResourceManagerExImpl
          .addTestResource("urn:test:simple.rng", toAbsolutePath("highlighting/simple.rng"), myTestRootDisposable);
        ExternalResourceManagerExImpl.addTestResource("urn:test:addressBook", toAbsolutePath("highlighting/rnc/addressbook.rnc"),
                                                      myTestRootDisposable);
        //m.addResource("http://www.w3.org/1999/XSL/Transform", toAbsolutePath("highlighting/relaxng.rng"));
        ExternalResourceManagerExImpl.addTestResource("http://www.w3.org/1999/XSL/Format", toAbsolutePath("highlighting/rnc/fo/main.rnc"),
                                                      myTestRootDisposable);
        ExternalResourceManagerExImpl.addTestResource("http://docbook.org/ns/docbook", toAbsolutePath("highlighting/docbook.rng"),
                                                      myTestRootDisposable);
        ExternalResourceManagerExImpl.addTestResource("urn:intelliForm:AttachmentFilter",
                                                      toAbsolutePath("highlighting/attachment-filter.rng"), myTestRootDisposable);
        ExternalResourceManagerExImpl
          .addTestResource("http://www.w3.org/1999/xhtml", toAbsolutePath("highlighting/html5/xhtml5.rnc"), myTestRootDisposable);

        m.addIgnoredResource("urn:intelliForm:Spaces");
        m.addIgnoredResource("http://www.w3.org/1999/xlink");
        m.addIgnoredResource("http://www.w3.org/2000/svg");
        m.addIgnoredResource("http://www.ascc.net/xml/schematron");
        m.addIgnoredResource("http://www.w3.org/2000/svg");
        m.addIgnoredResource("http://www.w3.org/1998/Math/MathML");
        m.addIgnoredResource("http://www.w3.org/1999/02/22-rdf-syntax-ns#");
        m.addIgnoredResource("http://nwalsh.com/xmlns/schema-control/");
        m.addIgnoredResource("http://xml.apache.org/fop/extensions");
        m.addIgnoredResource("http://www.antennahouse.com/names/XSL/Extensions");
        m.addIgnoredResource("http://www.renderx.com/XSL/Extensions");
        m.addIgnoredResource("http://relaxng.org/ns/compatibility/annotations/1.0");
      }
    });
  }

  public void testSimpleElement() throws Throwable {
    doHighlightingTest("simple-element_1.xml");
  }

  public void testOptionalElement() throws Throwable {
    doHighlightingTest("optional-element_1.xml");
  }

  public void testSimpleAttribute() throws Throwable {
    doHighlightingTest("simple-attribute_1.xml");
  }

  public void testSimpleAttributeMissing() throws Throwable {
    doHighlightingTest("simple-attribute_2.xml");
  }

  public void testOptionalAttribute() throws Throwable {
    doHighlightingTest("optional-attribute_1.xml");
    doHighlightingTest("optional-attribute_2.xml");
  }

  public void testFixedAttribute() throws Throwable {
    doHighlightingTest("fixed-attribute_1.xml");
  }

  public void testFixedAttributeIllegal() throws Throwable {
    doHighlightingTest("fixed-attribute_2.xml");
  }

  public void testValueChoice1() throws Throwable {
    doHighlightingTest("value-choice-1.xml");
  }

  public void testValueChoice2() throws Throwable {
    doHighlightingTest("value-choice-2.xml");
  }

  public void testTokenDatatype() throws Throwable {
    doHighlightingTest("token-datatype.xml");
  }

  public void testAttributeChoice1() throws Throwable {
    doHighlightingTest("attribute-choice-1.xml");
  }

  public void testAttributeChoice2() throws Throwable {
    doHighlightingTest("attribute-choice-2.xml");
  }

  public void testAttributeChoice3() throws Throwable {
    doHighlightingTest("attribute-choice-3.xml");
  }

  public void testNestedFragment() throws Throwable {
    doHighlightingTest("nested-fragment.xml");
  }

  public void testNestedFragment2() throws Throwable {
    myTestFixture.copyFileToProject("jpdl-3.1.xsd");
    doHighlightingTest("nested-fragment-2.xml");
  }

  public void testDocbookExample() throws Throwable {
    doHighlightingTest("mybook.xml");
  }

  public void testMissingAttributeRnc() throws Throwable {
    doHighlightingTest("rnc/missing-attribute.xml");
  }

  public void testValidRnc() throws Throwable {
    doHighlightingTest("rnc/valid-rnc.xml");
  }

  @CopyFile({"rnc/include.rnc", "rnc/included.rnc"})
  public void testValidIncludeRnc() throws Throwable {
    doHighlightingTest("rnc/valid-rnc-include.xml");
  }

  public void testBadElementRnc() throws Throwable {
    doHighlightingTest("rnc/bad-element.xml");
  }

  public void testRngSchema() throws Throwable {
    doHighlightingTest("relaxng.rng");
  }

  public void testNestedComposite() throws Throwable {
    doHighlightingTest("nested-composite.rng");
  }

  public void testXsltSchema() throws Throwable {
    doHighlightingTest("xslt.rng");
  }

  public void testXslFoRncSchema() throws Throwable {
    doHighlightingTest("rnc/fo/fo-test.xml");
  }

  public void testFoFromDocbook() throws Throwable {
    doHighlightingTest("rnc/fo/mybook.fo");
  }

  public void testDocBookSchema() throws Throwable {
    doHighlightingTest("docbook.rng");
  }

  public void testHtml5() throws Throwable {
    doHighlightingTest("Html5.xml");
  }

  public void testHtml5_2() throws Throwable {
    doHighlightingTest("Html5_2.xml");
  }
}
