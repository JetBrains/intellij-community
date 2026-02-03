// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.actions.styling;

import com.intellij.psi.tree.IElementType;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.jetbrains.annotations.NotNull;

public class ToggleItalicAction extends BaseToggleStateAction {

  @Override
  protected @NotNull String getBoundString(@NotNull CharSequence text, int selectionStart, int selectionEnd) {
    return isWord(text, selectionStart, selectionEnd) ? "_" : "*";
  }

  @Override
  protected boolean shouldMoveToWordBounds() {
    return true;
  }

  @Override
  protected @NotNull IElementType getTargetNodeType() {
    return MarkdownElementTypes.EMPH;
  }

  private static boolean isWord(@NotNull CharSequence text, int from, int to) {
    return (from == 0 || !Character.isLetterOrDigit(text.charAt(from - 1)))
           && (to == text.length() || !Character.isLetterOrDigit(text.charAt(to)));
  }
}
