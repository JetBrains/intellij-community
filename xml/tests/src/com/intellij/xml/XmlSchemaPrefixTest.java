/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.xml;

import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.xml.refactoring.SchemaPrefixRenameHandler;

/**
 * @author Konstantin Bulenkov
 */
public class XmlSchemaPrefixTest extends LightCodeInsightFixtureTestCase {

  public void testPrefixUsages() {
    doFindUsages("usages.xml", 16);
    doFindUsages("usages1.xml", 16);
  }

  public void testRename() {doRename();}
  public void testRename1() {doRename();}

  public void testRename2() {doRename();}
  public void testRenameFromClosingTag() {doRename();}

  private void doRename() {
    doRename("xsd");
  }

  private void doRename(String newValue) {
    final String name = getTestName(true);
    CodeInsightTestUtil.doInlineRenameTest(new SchemaPrefixRenameHandler(), name, "xml", newValue, myFixture);
  }

  @Override
  protected String getBasePath() {
    return "/xml/tests/testData/schemaPrefix";
  }

  protected void doFindUsages(String filename, int usages) {
    assertSize(usages, myFixture.testFindUsages(filename));
  }

}
