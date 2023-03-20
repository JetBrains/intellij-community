// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.plugins.markdown.ui.actions.styling;

import com.intellij.psi.tree.IElementType;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ToggleStrikethroughAction extends BaseToggleStateAction {

  @NotNull
  @Override
  protected String getBoundString(@NotNull CharSequence text, int selectionStart, int selectionEnd) {
    return "~~";
  }

  @Nullable
  @Override
  protected String getExistingBoundString(@NotNull CharSequence text, int startOffset) {
    return text.subSequence(startOffset, startOffset + 2).toString();
  }

  @Override
  protected boolean shouldMoveToWordBounds() {
    return true;
  }

  @NotNull
  @Override
  protected IElementType getTargetNodeType() {
    return MarkdownElementTypes.STRIKETHROUGH;
  }
}
