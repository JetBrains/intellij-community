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

@CopyFile("*.rnc")
public class RncIndexHighlightingTest extends AbstractIndexTest {

  @Override
  public String getTestDataPath() {
    return "highlighting/rnc";
  }

  public void testBackwardIncludeRef() throws Throwable {
    doHighlightingTest("backward-include-ref.rnc");
  }

  public void testInspectionUnusedDefine() throws Throwable {
    doHighlightingTest("unused-define.rnc");
  }

  public void testInspectionSuppressedUnusedDefine() throws Throwable {
    doHighlightingTest("suppressed-unused-define.rnc");
  }

  public void testInspectionUsedDefine() throws Throwable {
    doHighlightingTest("used-define.rnc");
  }

  public void testInclude1() throws Throwable {
    myTestFixture.configureByFiles("include1.rnc", "include1_1.rnc", "include1_2.rnc");
    doCustomHighlighting(true, false);
  }
}