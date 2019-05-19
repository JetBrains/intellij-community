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

import java.util.ArrayList;
import java.util.List;

public class RngXmlHighlightingTest extends HighlightingTestBase {
  @Override
  public String getTestDataPath() {
    return "highlighting";
  }

  @Override
  protected void init() {
    super.init();

    ApplicationManager.getApplication().runWriteAction(() -> {
      ExternalResourceManagerExImpl
        .registerResourceTemporarily("urn:test:simple.rng", toAbsolutePath("highlighting/simple.rng"), getTestRootDisposable());
      ExternalResourceManagerExImpl.registerResourceTemporarily("urn:test:addressBook", toAbsolutePath("highlighting/rnc/addressbook.rnc"),
                                                    getTestRootDisposable());
      //m.addResource("http://www.w3.org/1999/XSL/Transform", toAbsolutePath("highlighting/relaxng.rng"));
      ExternalResourceManagerExImpl.registerResourceTemporarily("http://www.w3.org/1999/XSL/Format", toAbsolutePath("highlighting/rnc/fo/main.rnc"),
                                                    getTestRootDisposable());
      ExternalResourceManagerExImpl.registerResourceTemporarily("http://docbook.org/ns/docbook", toAbsolutePath("highlighting/docbook.rng"),
                                                    getTestRootDisposable());
      ExternalResourceManagerExImpl.registerResourceTemporarily("urn:intelliForm:AttachmentFilter",
                                                    toAbsolutePath("highlighting/attachment-filter.rng"), getTestRootDisposable());
      ExternalResourceManagerExImpl
        .registerResourceTemporarily("http://www.w3.org/1999/xhtml", toAbsolutePath("highlighting/html5/xhtml5.rnc"), getTestRootDisposable());

      List<String> list = new ArrayList<>();
      list.add("urn:intelliForm:Spaces");
      list.add("http://www.w3.org/1999/xlink");
      list.add("http://www.w3.org/2000/svg");
      list.add("http://www.ascc.net/xml/schematron");
      list.add("http://www.w3.org/2000/svg");
      list.add("http://www.w3.org/1998/Math/MathML");
      list.add("http://www.w3.org/1999/02/22-rdf-syntax-ns#");
      list.add("http://nwalsh.com/xmlns/schema-control/");
      list.add("http://xml.apache.org/fop/extensions");
      list.add("http://www.antennahouse.com/names/XSL/Extensions");
      list.add("http://www.renderx.com/XSL/Extensions");
      list.add("http://relaxng.org/ns/compatibility/annotations/1.0");
      ExternalResourceManagerEx.getInstanceEx().addIgnoredResources(list, getTestRootDisposable());
    });
  }

  public void testSimpleElement() {
    doHighlightingTest("simple-element_1.xml");
  }

  public void testOptionalElement() {
    doHighlightingTest("optional-element_1.xml");
  }

  public void testSimpleAttribute() {
    doHighlightingTest("simple-attribute_1.xml");
  }

  public void testSimpleAttributeMissing() {
    doHighlightingTest("simple-attribute_2.xml");
  }

  public void testOptionalAttribute() {
    doHighlightingTest("optional-attribute_1.xml");
    doHighlightingTest("optional-attribute_2.xml");
  }

  public void testFixedAttribute() {
    doHighlightingTest("fixed-attribute_1.xml");
  }

  public void testFixedAttributeIllegal() {
    doHighlightingTest("fixed-attribute_2.xml");
  }

  public void testValueChoice1() {
    doHighlightingTest("value-choice-1.xml");
  }

  public void testValueChoice2() {
    doHighlightingTest("value-choice-2.xml");
  }

  public void testTokenDatatype() {
    doHighlightingTest("token-datatype.xml");
  }

  public void testAttributeChoice1() {
    doHighlightingTest("attribute-choice-1.xml");
  }

  public void testAttributeChoice2() {
    doHighlightingTest("attribute-choice-2.xml");
  }

  public void testAttributeChoice3() {
    doHighlightingTest("attribute-choice-3.xml");
  }

  public void testNestedFragment() {
    doHighlightingTest("nested-fragment.xml");
  }

  public void testNestedFragment2() {
    myTestFixture.copyFileToProject("jpdl-3.1.xsd");
    doHighlightingTest("nested-fragment-2.xml");
  }

  public void testDocbookExample() {
    doHighlightingTest("mybook.xml");
  }

  public void testMissingAttributeRnc() {
    doHighlightingTest("rnc/missing-attribute.xml");
  }

  public void testValidRnc() {
    doHighlightingTest("rnc/valid-rnc.xml");
  }

  @CopyFile({"rnc/include.rnc", "rnc/included.rnc"})
  public void testValidIncludeRnc() {
    doHighlightingTest("rnc/valid-rnc-include.xml");
  }

  public void testBadElementRnc() {
    doHighlightingTest("rnc/bad-element.xml");
  }

  public void testRngSchema() {
    doHighlightingTest("relaxng.rng");
  }

  public void testNestedComposite() {
    doHighlightingTest("nested-composite.rng");
  }

  public void testXsltSchema() {
    doHighlightingTest("xslt.rng");
  }

  public void testXslFoRncSchema() {
    doHighlightingTest("rnc/fo/fo-test.xml");
  }

  public void testFoFromDocbook() {
    doHighlightingTest("rnc/fo/mybook.fo");
  }

  public void testDocBookSchema() {
    doHighlightingTest("docbook.rng");
  }

  public void testHtml5() {
    doHighlightingTest("Html5.xml");
  }

  public void testHtml5_2() {
    doHighlightingTest("Html5_2.xml");
  }
}
