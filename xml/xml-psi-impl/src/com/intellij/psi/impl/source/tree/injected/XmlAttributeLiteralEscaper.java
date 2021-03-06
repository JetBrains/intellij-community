/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.psi.LiteralTextEscaper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.impl.source.xml.XmlAttributeValueImpl;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NotNull;

public class XmlAttributeLiteralEscaper extends LiteralTextEscaper<XmlAttributeValueImpl> {
  private final XmlAttribute myXmlAttribute;

  public XmlAttributeLiteralEscaper(@NotNull XmlAttributeValueImpl host) {
    super(host);
    PsiElement parent = host.getParent();
    myXmlAttribute = parent instanceof XmlAttribute ? (XmlAttribute)parent :
                     XmlElementFactory.getInstance(host.getProject()).createAttribute("a", host.getValue(), parent);
  }

  @Override
  public boolean decode(@NotNull final TextRange rangeInsideHost, @NotNull StringBuilder outChars) {
    String displayValue = myXmlAttribute.getDisplayValue();
    if (displayValue == null) {
      return true;
    }
    TextRange valueTextRange = myXmlAttribute.getValueTextRange();
    int startInDecoded = myXmlAttribute.physicalToDisplay(rangeInsideHost.getStartOffset() - valueTextRange.getStartOffset());
    int endInDecoded = myXmlAttribute.physicalToDisplay(rangeInsideHost.getEndOffset() - valueTextRange.getStartOffset());
    startInDecoded = Math.max(0, Math.min(startInDecoded, displayValue.length()));
    endInDecoded = Math.max(0, Math.min(endInDecoded, displayValue.length()));
    if (startInDecoded > endInDecoded) endInDecoded = startInDecoded;
    outChars.append(displayValue, startInDecoded, endInDecoded);
    return true;
  }

  @Override
  public int getOffsetInHost(final int offsetInDecoded, @NotNull final TextRange rangeInsideHost) {
    TextRange valueTextRange = myXmlAttribute.getValueTextRange();
    int displayStart = myXmlAttribute.physicalToDisplay(rangeInsideHost.getStartOffset()-valueTextRange.getStartOffset());

    int dp = myXmlAttribute.displayToPhysical(offsetInDecoded + displayStart);
    if (dp == -1) return -1;
    return dp + valueTextRange.getStartOffset();
  }

  @Override
  public boolean isOneLine() {
    return true;
  }

  @NotNull
  @Override
  public TextRange getRelevantTextRange() {
    return myXmlAttribute.getValueTextRange();
  }
}
