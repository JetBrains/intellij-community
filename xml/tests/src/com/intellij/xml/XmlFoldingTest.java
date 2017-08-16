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

import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

import java.io.File;

public class XmlFoldingTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testTagFolding() { doTest(); }

  public void testStyleAttributeFolding() { doTest(); }
  public void testStyleAttributeFolding2() { doTest(".xml"); }

  public void testEntities() { doTest(); }

  public void testDataUri() { doTest(); }

  public void testCustomRegions() { doTest(); }

  private void doTest() {
    doTest(".html");
  }

  private void doTest(String extension) {
    myFixture.testFolding(getTestDataPath() + getTestName(true) + extension);
  }

  @Override
  protected String getBasePath() {
    return "/xml/tests/testData/folding/";
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + getBasePath();
  }
}
