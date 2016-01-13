/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.jetbrains.python.configuration;

import com.intellij.openapi.diff.DiffContent;
import com.intellij.openapi.diff.SimpleContent;
import com.intellij.openapi.diff.impl.settings.DiffPreviewProvider;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NonNls;

/**
 * @author oleg
 */
public class PyDiffPreviewProvider extends DiffPreviewProvider {
  @Override
  public DiffContent[] createContents() {
    return new DiffContent[]{createContent(LEFT_TEXT), createContent(CENTER_TEXT), createContent(RIGHT_TEXT)};
  }

  private static SimpleContent createContent(final String text) {
    return new SimpleContent(text, PythonFileType.INSTANCE);
  }

  @NonNls private static final String LEFT_TEXT =   "class MyClass\n" +
                                                    "  value = 123\n" +
                                                    "\n" +
                                                    "  def left_only(self):\n" +
                                                    "    bar(123)\n" +
                                                    "\n" +
                                                    "  def foo(self):\n" +
                                                    "    # Left changes\n" +
                                                    "    pass\n" +
                                                    "\n" +
                                                    "  def bar(self, a, b)\n" +
                                                    "\n" +
                                                    "    print a\n" +
                                                    "    print b";
  @NonNls private static final String CENTER_TEXT = "class MyClass\n" +
                                                    "  value = 123\n" +
                                                    "\n" +
                                                    "  def foo(self):\n" +
                                                    "    pass\n" +
                                                    "\n" +
                                                    "  def removed_from_left(self):\n" +
                                                    "    bar('PyCharmRulezzz')\n" +
                                                    "\n" +
                                                    "  def bar(self, a, b)\n" +
                                                    "\n" +
                                                    "    print a\n" +
                                                    "    print b";
  @NonNls private static final String RIGHT_TEXT =  "class MyClass\n" +
                                                    "  value = -123\n" +
                                                    "\n" +
                                                    "  def foo(self):\n" +
                                                    "    # Right changes\n" +
                                                    "    pass\n" +
                                                    "\n" +
                                                    "  def removed_from_left(self):\n" +
                                                    "    bar('PyCharmRulezzz')\n" +
                                                    "\n" +
                                                    "  def bar(self, a, b)\n" +
                                                    "    print a\n" +
                                                    "\n" +
                                                    "    print b";
}
