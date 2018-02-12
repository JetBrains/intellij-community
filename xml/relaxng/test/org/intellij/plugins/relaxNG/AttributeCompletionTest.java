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
public class AttributeCompletionTest extends HighlightingTestBase {
  @Override
  public String getTestDataPath() {
    return "completion";
  }

  public void testAttributeCompletion1() {
    doTestCompletion("attribute-completion-1.xml", new String[]{ "foo" });
  }

  public void testAttributeCompletion2() {
    doTestCompletion("attribute-completion-2.xml", new String[]{ "1.0" });
  }

  public void testAttributeCompletion3() {
    doTestCompletion("attribute-completion-3.xml", "1.0", "2.0");
  }

  public void testAttributeCompletion4() {
    doTestCompletion("attribute-completion-4.xml", "1.0", "2.0");
  }

  public void testAttributeCompletion5() {
    doTestCompletion("attribute-completion-5.xml", "foo", "bar");
  }

  public void testAttributeCompletion6() {
    doTestCompletion("attribute-completion-6.xml", "p:foo", "p:bar");
  }

  public void testAttrCombine() {
    doTestCompletion("attr-combine.xml", "foo", "bar");
  }
}