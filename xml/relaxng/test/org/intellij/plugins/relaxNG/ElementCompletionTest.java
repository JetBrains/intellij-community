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

import com.intellij.application.options.editor.WebEditorOptions;
import org.intellij.plugins.testUtil.CopyFile;

@CopyFile("element-completion.rng")
public class ElementCompletionTest extends HighlightingTestBase {
  @Override
  public String getTestDataPath() {
    return "completion";
  }

  public void testElementCompletionRoot() {
    doTestCompletion("element-completion-root.xml", "bar", "baz", "completion-1", "completion-2", "completion-3", "foo", "x");
  }

  public void testElementCompletion1() {
    doTestCompletion("element-completion-1.xml", new String[]{ "foo" });
  }

  public void testElementCompletion2() {
    doTestCompletion("element-completion-2.xml", "foo", "bar", "baz");
  }

  public void testElementCompletion3() {
    final WebEditorOptions options = WebEditorOptions.getInstance();
    final boolean oldValue = options.isAutomaticallyInsertRequiredAttributes();
    try {
      options.setAutomaticallyInsertRequiredAttributes(false);
      doTestCompletion("element-completion-3", "xml");
    }
    finally {
      options.setAutomaticallyInsertRequiredAttributes(oldValue);
    }
  }
}