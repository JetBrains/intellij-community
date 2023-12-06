// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.impl.source.xml.XmlTextImpl;
import org.jetbrains.annotations.NotNull;

public class XmlTextLiteralEscaper extends LiteralTextEscaper<XmlTextImpl> {
  public XmlTextLiteralEscaper(@NotNull XmlTextImpl xmlText) {
    super(xmlText);
  }

  @Override
  public boolean decode(final @NotNull TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
    int startInDecoded = myHost.physicalToDisplay(rangeInsideHost.getStartOffset());
    int endInDecoded = myHost.physicalToDisplay(rangeInsideHost.getEndOffset());
    outChars.append(myHost.getValue(), startInDecoded, endInDecoded);
    return true;
  }

  @Override
  public int getOffsetInHost(final int offsetInDecoded, final @NotNull TextRange rangeInsideHost) {
    final int rangeInsideHostStartOffset = rangeInsideHost.getStartOffset();
    int displayStart = myHost.physicalToDisplay(rangeInsideHostStartOffset);

    int i = myHost.displayToPhysical(offsetInDecoded + displayStart);
    if (i < rangeInsideHostStartOffset) i = rangeInsideHostStartOffset;
    final int rangeInsideHostEndOffset = rangeInsideHost.getEndOffset();
    if (i > rangeInsideHostEndOffset) i = rangeInsideHostEndOffset;
    return i;
  }

  @Override
  public @NotNull TextRange getRelevantTextRange() {
    return myHost.getCDATAInterior();
  }

  @Override
  public boolean isOneLine() {
    return false;
  }
}
