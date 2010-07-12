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

import com.intellij.application.options.editor.WebEditorOptions;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 07.08.2007
*/
@CopyFile("element-completion.rng")
public class ElementCompletionTest extends HighlightingTestBase {
  public String getTestDataPath() {
    return "completion";
  }

  public void testElementCompletionRoot() throws Throwable {
    doTestCompletion("element-completion-root.xml", "completion-1", "completion-2", "completion-3", "x");
  }

  public void testElementCompletion1() throws Throwable {
    doTestCompletion("element-completion-1.xml", new String[]{ "foo" });
  }

  public void testElementCompletion2() throws Throwable {
    doTestCompletion("element-completion-2.xml", "foo", "bar", "baz");
  }

  public void testElementCompletion3() throws Throwable {
    WebEditorOptions.getInstance().setAutomaticallyInsertRequiredAttributes(false);
    doTestCompletion("element-completion-3", "xml");
  }
}