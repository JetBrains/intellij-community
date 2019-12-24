// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions.styling;

import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

public class HeaderDownAction extends MarkdownHeaderAction {

  @NotNull
  @Override
  protected Function<Integer, Integer> getLevelFunction() {
    return integer -> integer + 1;
  }
}