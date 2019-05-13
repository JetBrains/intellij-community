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

public class RncExternalHighlightingTest extends HighlightingTestBase {

  @Override
  public String getTestDataPath() {
    return "highlighting/rnc";
  }

  @Override
  protected void init() {
//    new ProjectLoader(myTestFixture.getProject()).initComponent();
//    ExternalResourceManager.getInstance().addResource("http://relaxng.org/ns/structure/1.0", new File("highlighting/relaxng.rng").getAbsolutePath());
  }

  public void testAddressBook() {
    doExternalToolHighlighting("addressbook.rnc");
  }

  public void testMissingContent() {
    doExternalToolHighlighting("missing-content.rnc");
  }

  @CopyFile("included.rnc")
  public void testMissingStart() {
    doExternalToolHighlighting("missing-start.rnc");
  }

  public void testUndefinedRef() {
    doExternalToolHighlighting("undefined-ref-ok.rnc");
  }

  public void testRngSchema() {
    doExternalToolHighlighting("rng-schema.rnc");
  }
}