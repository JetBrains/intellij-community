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

/**
 * Created by IntelliJ IDEA.
 * User: sweinreuter
 * Date: 25.07.2007
 */
public class RncExternalHighlightingTest extends HighlightingTestBase {

  public String getTestDataPath() {
    return "highlighting/rnc";
  }

  protected void init() {
//    new ProjectLoader(myTestFixture.getProject()).initComponent();
//    ExternalResourceManager.getInstance().addResource("http://relaxng.org/ns/structure/1.0", new File("highlighting/relaxng.rng").getAbsolutePath());
  }

  public void testAddressBook() throws Throwable {
    doExternalToolHighlighting("addressbook.rnc");
  }

  public void testMissingContent() throws Throwable {
    doExternalToolHighlighting("missing-content.rnc");
  }

  @CopyFile("included.rnc")
  public void testMissingStart() throws Throwable {
    doExternalToolHighlighting("missing-start.rnc");
  }

  public void testUndefinedRef() throws Throwable {
    doExternalToolHighlighting("undefined-ref-ok.rnc");
  }

  public void testRngSchema() throws Throwable {
    doExternalToolHighlighting("rng-schema.rnc");
  }
}