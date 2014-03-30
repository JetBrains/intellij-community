/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.source.tree.injected;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.psi.impl.source.xml.XmlTextImpl;
import com.intellij.psi.LiteralTextEscaper;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
*/
public class XmlTextLiteralEscaper extends LiteralTextEscaper<XmlTextImpl> {
  public XmlTextLiteralEscaper(final XmlTextImpl xmlText) {
    super(xmlText);
  }

  public boolean decode(@NotNull final TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
    ProperTextRange.assertProperRange(rangeInsideHost);
    int startInDecoded = myHost.physicalToDisplay(rangeInsideHost.getStartOffset());
    int endInDecoded = myHost.physicalToDisplay(rangeInsideHost.getEndOffset());
    outChars.append(myHost.getValue(), startInDecoded, endInDecoded);
    return true;
  }

  public int getOffsetInHost(final int offsetInDecoded, @NotNull final TextRange rangeInsideHost) {
    final int rangeInsideHostStartOffset = rangeInsideHost.getStartOffset();
    int displayStart = myHost.physicalToDisplay(rangeInsideHostStartOffset);

    int i = myHost.displayToPhysical(offsetInDecoded + displayStart);
    if (i < rangeInsideHostStartOffset) i = rangeInsideHostStartOffset;
    final int rangeInsideHostEndOffset = rangeInsideHost.getEndOffset();
    if (i > rangeInsideHostEndOffset) i = rangeInsideHostEndOffset;
    return i;
  }

  @NotNull
  public TextRange getRelevantTextRange() {
    return myHost.getCDATAInterior();
  }

  public boolean isOneLine() {
    return false;
  }
}
