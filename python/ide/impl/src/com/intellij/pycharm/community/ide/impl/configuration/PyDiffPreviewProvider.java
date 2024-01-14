// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration;

import com.intellij.diff.contents.DiffContent;
import com.intellij.openapi.diff.impl.settings.DiffPreviewProvider;
import com.jetbrains.python.PythonFileType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class PyDiffPreviewProvider extends DiffPreviewProvider {
  @Override
  public DiffContent @NotNull [] createContents() {
    return createContent(LEFT_TEXT, CENTER_TEXT, RIGHT_TEXT, PythonFileType.INSTANCE);
  }

  @NonNls private static final String LEFT_TEXT = """
    class MyClass
      value = 123

      def left_only(self):
        bar(123)

      def foo(self):
        # Left changes
        pass

      def bar(self, a, b)

        print a
        print b



    """;
  @NonNls private static final String CENTER_TEXT = """
    class MyClass
      value = 123

      def foo(self):
        pass

      def removed_from_left(self):
        bar('PyCharmRulezzz')

      def bar(self, a, b)

        print a
        print b



    """;
  @NonNls private static final String RIGHT_TEXT = """
    class MyClass
      value = -123

      def foo(self):
        # Right changes
        pass

      def removed_from_left(self):
        bar('PyCharmRulezzz')

      def bar(self, a, b)
        print a

        print b



    """;
}
