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

@CopyFile("*.rng")
public class RngIndexHighlightingTest extends AbstractIndexTest {

  @Override
  public String getTestDataPath() {
    return "highlighting/rng";
  }

  public void testInspectionUnusedDefine1() {
    doHighlightingTest("unused-define-1.rng");
  }

  public void testInspectionUnusedDefine2() {
    doHighlightingTest("unused-define-2.rng");
  }

  public void testInspectionDefine1() {
    doHighlightingTest("used-define-1.rng");
  }

  public void testInspectionDefine2() {
    doHighlightingTest("used-define-2.rng");
  }

  public void testInspectionDefine3() {
    doHighlightingTest("used-define-3-include.rng");
  }

  public void testIncludedGrammarWithoutStart() {
    // adding a <weak_warning> tag doesn't work because it seems to prevent the index to recognize the file
    // because the replacement is done *after* building the index :(
    doCustomHighlighting("included-grammar.rng", false, false);
  }

  public void testBackwardIncludeRef() {
    doHighlightingTest("backward-include-ref.rng");
  }

  public void testUnrelatedBackwardIncludeRef() {
    doHighlightingTest("backward-with-include.rng");
  }
}