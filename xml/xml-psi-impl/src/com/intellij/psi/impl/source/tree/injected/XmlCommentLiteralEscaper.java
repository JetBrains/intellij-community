// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.impl.source.xml.XmlCommentImpl;
import org.jetbrains.annotations.NotNull;

public class XmlCommentLiteralEscaper extends LiteralTextEscaper<XmlCommentImpl> {
  public XmlCommentLiteralEscaper(@NotNull XmlCommentImpl host) {
    super(host);
  }

  @Override
  public boolean decode(final @NotNull TextRange rangeInsideHost, final @NotNull StringBuilder outChars) {
    outChars.append(myHost.getText(), rangeInsideHost.getStartOffset(), rangeInsideHost.getEndOffset());
    return true;
  }

  @Override
  public int getOffsetInHost(final int offsetInDecoded, final @NotNull TextRange rangeInsideHost) {
    int offset = offsetInDecoded + rangeInsideHost.getStartOffset();
    if (offset < rangeInsideHost.getStartOffset()) offset = rangeInsideHost.getStartOffset();
    if (offset > rangeInsideHost.getEndOffset()) offset = rangeInsideHost.getEndOffset();
    return offset;
  }

  @Override
  public boolean isOneLine() {
    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(myHost.getLanguage());
    if (commenter instanceof CodeDocumentationAwareCommenter) {
      return myHost.getTokenType() == ((CodeDocumentationAwareCommenter) commenter).getLineCommentTokenType();
    }
    return false;
  }
}
