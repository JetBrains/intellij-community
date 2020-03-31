// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.configuration;

import com.intellij.diff.contents.DiffContent;
import com.intellij.openapi.diff.impl.settings.DiffPreviewProvider;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class PyDiffPreviewProvider extends DiffPreviewProvider {
  @Override
  public DiffContent @NotNull [] createContents() {
    return createContent(LEFT_TEXT, CENTER_TEXT, RIGHT_TEXT, PythonFileType.INSTANCE);
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
                                                    "    print b\n" +
                                                    "\n" +
                                                    "\n" +
                                                    "\n";
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
                                                    "    print b\n" +
                                                    "\n" +
                                                    "\n" +
                                                    "\n";
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
                                                    "    print b\n" +
                                                    "\n" +
                                                    "\n" +
                                                    "\n";
}
