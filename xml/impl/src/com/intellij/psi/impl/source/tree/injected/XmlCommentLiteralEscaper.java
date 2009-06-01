package com.intellij.psi.impl.source.tree.injected;

import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.impl.source.xml.XmlCommentImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import org.jetbrains.annotations.NotNull;

/**
 * @author spleaner
 */
public class XmlCommentLiteralEscaper extends LiteralTextEscaper<XmlCommentImpl> {
  public XmlCommentLiteralEscaper(@NotNull XmlCommentImpl host) {
    super(host);
  }

  public boolean decode(@NotNull final TextRange rangeInsideHost, @NotNull final StringBuilder outChars) {
    ProperTextRange.assertProperRange(rangeInsideHost);
    outChars.append(myHost.getText(), rangeInsideHost.getStartOffset(), rangeInsideHost.getEndOffset());
    return true;
  }

  public int getOffsetInHost(final int offsetInDecoded, @NotNull final TextRange rangeInsideHost) {
    int offset = offsetInDecoded + rangeInsideHost.getStartOffset();
    if (offset < rangeInsideHost.getStartOffset()) offset = rangeInsideHost.getStartOffset();
    if (offset > rangeInsideHost.getEndOffset()) offset = rangeInsideHost.getEndOffset();
    return offset;
  }

  public boolean isOneLine() {
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(myHost.getLanguage());
    if (commenter instanceof CodeDocumentationAwareCommenter) {
      return myHost.getTokenType() == ((CodeDocumentationAwareCommenter) commenter).getLineCommentTokenType();
    }
    return false;
  }
}
