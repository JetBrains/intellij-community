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

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 07.08.2007
*/
@CopyFile("*.rng")
public class AttributeCompletionTest extends HighlightingTestBase {
  public String getTestDataPath() {
    return "completion";
  }

  public void testAttributeCompletion1() throws Throwable {
    doTestCompletion("attribute-completion-1.xml", new String[]{ "foo" });
  }

  public void testAttributeCompletion2() throws Throwable {
    doTestCompletion("attribute-completion-2.xml", new String[]{ "1.0" });
  }

  public void testAttributeCompletion3() throws Throwable {
    doTestCompletion("attribute-completion-3.xml", "1.0", "2.0");
  }

  public void testAttributeCompletion4() throws Throwable {
    doTestCompletion("attribute-completion-4.xml", "1.0", "2.0");
  }

  public void testAttributeCompletion5() throws Throwable {
    doTestCompletion("attribute-completion-5.xml", "foo", "bar");
  }

  public void testAttributeCompletion6() throws Throwable {
    doTestCompletion("attribute-completion-6.xml", "p:foo", "p:bar");
  }

  public void testAttrCombine() throws Throwable {
    doTestCompletion("attr-combine.xml", "foo", "bar");
  }
}