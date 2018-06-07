/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.yaml.folding;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

public class YAMLFoldingTest extends LightPlatformCodeInsightFixtureTestCase {

  public static final String RELATIVE_TEST_DATA_PATH = "/plugins/yaml/testSrc/org/jetbrains/yaml/";
  public static final String TEST_DATA_PATH = PathManagerEx.getCommunityHomePath() + RELATIVE_TEST_DATA_PATH;

  public void testFolding() {
    defaultTest();
  }

  public void testSequenceFolding() {
    defaultTest();
  }

  public void testRuby18677() {
    defaultTest();
  }

  public void testRegionFolding() {
    defaultTest();
  }

  public void defaultTest() {
    myFixture.testFolding(getTestDataPath() + getTestName(true) + ".yaml");
  }

  @Override
  protected String getTestDataPath() {
    return TEST_DATA_PATH + "/folding/data/";
  }
} 