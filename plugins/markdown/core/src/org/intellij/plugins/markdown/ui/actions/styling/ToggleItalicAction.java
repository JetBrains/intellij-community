package org.intellij.plugins.markdown.ui.actions.styling;

import com.intellij.psi.tree.IElementType;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.jetbrains.annotations.NotNull;

public class ToggleItalicAction extends BaseToggleStateAction {

  @Override
  @NotNull
  protected String getBoundString(@NotNull CharSequence text, int selectionStart, int selectionEnd) {
    return isWord(text, selectionStart, selectionEnd) ? "_" : "*";
  }

  @Override
  protected boolean shouldMoveToWordBounds() {
    return true;
  }

  @Override
  @NotNull
  protected IElementType getTargetNodeType() {
    return MarkdownElementTypes.EMPH;
  }

  private static boolean isWord(@NotNull CharSequence text, int from, int to) {
    return (from == 0 || !Character.isLetterOrDigit(text.charAt(from - 1)))
           && (to == text.length() || !Character.isLetterOrDigit(text.charAt(to)));
  }
}
