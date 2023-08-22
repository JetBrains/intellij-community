// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.xml;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.XmlAttributeValuePattern;
import com.intellij.psi.PsiLiteralValue;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface XmlAttributeValue extends XmlElement, PsiLiteralValue, XmlNamedReferenceHost {
  /**
   * @return text inside XML attribute with quotes stripped off
   */
  @Override
  @NotNull
  @NlsSafe
  String getValue();

  /**
   * @return the range occupied by text inside XML attribute with quotes stripped off
   */
  TextRange getValueTextRange();

  @Experimental
  @Nullable
  @Override
  default String getHostName() {
    return XmlAttributeValuePattern.getLocalName(this);
  }
}
