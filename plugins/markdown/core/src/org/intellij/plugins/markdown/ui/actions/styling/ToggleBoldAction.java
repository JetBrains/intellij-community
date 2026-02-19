// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.markdown.ui.actions.styling;

import com.intellij.psi.tree.IElementType;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ToggleBoldAction extends BaseToggleStateAction {

  @Override
  protected @NotNull String getBoundString(@NotNull CharSequence text, int selectionStart, int selectionEnd) {
    return "**";
  }

  @Override
  protected @Nullable String getExistingBoundString(@NotNull CharSequence text, int startOffset) {
    return text.subSequence(startOffset, startOffset + 2).toString();
  }

  @Override
  protected boolean shouldMoveToWordBounds() {
    return true;
  }

  @Override
  protected @NotNull IElementType getTargetNodeType() {
    return MarkdownElementTypes.STRONG;
  }
}
