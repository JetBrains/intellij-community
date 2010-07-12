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

import org.intellij.plugins.testUtil.CopyFile;

import com.intellij.javaee.ExternalResourceManager;

import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 25.07.2007
 */
public class XmlValidationTest extends HighlightingTestBase {

  public void testValidDocument() throws Throwable {
    doTest("xslt.rng");
  }

  @CopyFile("broken.rng")
  public void testPartiallyBrokenRng() throws Throwable {
    myTestFixture.testHighlighting("broken-rng.xml");
  }

  @CopyFile("broken.rnc")
  public void testPartiallyBrokenRnc() throws Throwable {
    myTestFixture.testHighlighting("broken-rnc.xml");
  }

  @CopyFile("entity-included.xml")
  public void testEntityRef1() throws Throwable {
    doTest("entity-test-1.xml");
  }

  @CopyFile("entity-included.xml")
  public void testEntityRef2() throws Throwable {
    doTest("entity-test-2.xml");
  }

  public void testEntityRef3() throws Throwable {
    doTest("entity-test-3.xml");
  }

  public void testTextContent() throws Throwable {
    doTest("text-content.xml");
  }

  public void testCDATA() throws Throwable {
    doTest("cdata-test.xml");
  }

  public void testMissingElement() throws Throwable {
    doTest("missing-element.xml");
  }

  public void testInvalidElement() throws Throwable {
    doTest("invalid-element.xml");
  }

  public void testInvalidElementRnc() throws Throwable {
    doTest("invalid-element-rnc.xml");
  }

  public void testMissingElementRnc() throws Throwable {
    doTest("missing-element-rnc.xml");
  }

  private void doTest(String name) throws Throwable {
    doExternalToolHighlighting(name);
  }

  protected void init() {
    super.init();
    final ExternalResourceManager mgr = ExternalResourceManager.getInstance();
    mgr.addResource("urn:test:simple.rng", toAbsolutePath("validation/simple.rng"));
    mgr.addResource("urn:test:simple.rnc", toAbsolutePath("validation/simple.rnc"));
    mgr.addResource("http://www.w3.org/1999/XSL/Transform", toAbsolutePath("validation/relaxng.rng"));
  }

  public String getTestDataPath() {
    return "validation";
  }
}