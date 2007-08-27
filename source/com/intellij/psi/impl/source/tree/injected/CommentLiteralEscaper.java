package com.intellij.psi.impl.source.tree.injected;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.impl.source.tree.PsiCommentImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
*/
public class CommentLiteralEscaper extends LiteralTextEscaper<PsiCommentImpl> {
  public CommentLiteralEscaper(PsiCommentImpl host) {
    super(host);
  }

  public boolean decode(@NotNull final TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
    outChars.append(myHost.getText(), rangeInsideHost.getStartOffset(), rangeInsideHost.getEndOffset());
    return true;
  }

  public int getOffsetInHost(int offsetInDecoded, @NotNull final TextRange rangeInsideHost) {
    int offset = offsetInDecoded + rangeInsideHost.getStartOffset();
    if (offset < rangeInsideHost.getStartOffset()) offset = rangeInsideHost.getStartOffset();
    if (offset > rangeInsideHost.getEndOffset()) offset = rangeInsideHost.getEndOffset();
    return offset;
  }

  public boolean isOneLine() {
    return myHost.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT;
  }
}