package org.intellij.plugins.markdown.ui.actions.styling;

import com.intellij.psi.tree.IElementType;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ToggleBoldAction extends BaseToggleStateAction {

  @NotNull
  @Override
  protected String getBoundString(@NotNull CharSequence text, int selectionStart, int selectionEnd) {
    return "**";
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
    return MarkdownElementTypes.STRONG;
  }
}
