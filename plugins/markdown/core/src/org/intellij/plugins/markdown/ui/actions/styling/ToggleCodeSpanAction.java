// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.actions.styling;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ToggleCodeSpanAction extends BaseToggleStateAction {

  @Override
  protected @NotNull String getBoundString(@NotNull CharSequence text, int selectionStart, int selectionEnd) {
    int maxBacktickSequenceSeen = 0;
    int curBacktickSequence = 0;
    for (int i = selectionStart; i < selectionEnd; ++i) {
      if (text.charAt(i) != '`') {
        curBacktickSequence = 0;
      }
      else {
        curBacktickSequence++;
        maxBacktickSequenceSeen = Math.max(maxBacktickSequenceSeen, curBacktickSequence);
      }
    }

    return StringUtil.repeat("`", maxBacktickSequenceSeen + 1);
  }

  @Override
  protected @Nullable String getExistingBoundString(@NotNull CharSequence text, int startOffset) {
    int to = startOffset;
    while (to < text.length() && text.charAt(to) == '`') {
      to++;
    }

    return text.subSequence(startOffset, to).toString();
  }

  @Override
  protected boolean shouldMoveToWordBounds() {
    return false;
  }

  @Override
  protected @NotNull IElementType getTargetNodeType() {
    return MarkdownElementTypes.CODE_SPAN;
  }
}
