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

import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.application.ApplicationManager;
import org.intellij.plugins.testUtil.CopyFile;

public class RngXmlValidationTest extends HighlightingTestBase {

  public void testValidDocument() {
    doTest("xslt.rng");
  }

  @CopyFile("broken.rng")
  public void testPartiallyBrokenRng() {
    myTestFixture.testHighlighting("broken-rng.xml");
  }

  @CopyFile("broken.rnc")
  public void testPartiallyBrokenRnc() {
    myTestFixture.testHighlighting("broken-rnc.xml");
  }

  @CopyFile("entity-included.xml")
  public void testEntityRef1() {
    doTest("entity-test-1.xml");
  }

  @CopyFile("entity-included.xml")
  public void testEntityRef2() {
    doTest("entity-test-2.xml");
  }

  public void testEntityRef3() {
    doTest("entity-test-3.xml");
  }

  public void testTextContent() {
    doTest("text-content.xml");
  }

  public void testCDATA() {
    doTest("cdata-test.xml");
  }

  public void testMissingElement() {
    doTest("missing-element.xml");
  }

  public void testInvalidElement() {
    doTest("invalid-element.xml");
  }

  public void testInvalidElementRnc() {
    doTest("invalid-element-rnc.xml");
  }

  public void testMissingElementRnc() {
    doTest("missing-element-rnc.xml");
  }

  private void doTest(String name) {
    doExternalToolHighlighting(name);
  }

  @Override
  protected void init() {
    super.init();
    ApplicationManager.getApplication().runWriteAction(() -> {
      final ExternalResourceManager mgr = ExternalResourceManager.getInstance();
      mgr.addResource("urn:test:simple.rng", toAbsolutePath("validation/simple.rng"));
      mgr.addResource("urn:test:simple.rnc", toAbsolutePath("validation/simple.rnc"));
      //mgr.addResource("http://www.w3.org/1999/XSL/Transform", toAbsolutePath("validation/relaxng.rng"));
    });
  }

  @Override
  public String getTestDataPath() {
    return "validation";
  }
}