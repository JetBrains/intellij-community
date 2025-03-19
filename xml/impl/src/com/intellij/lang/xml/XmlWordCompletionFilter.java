// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lang.xml;

import com.intellij.lang.DefaultWordCompletionFilter;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;

public class XmlWordCompletionFilter extends DefaultWordCompletionFilter {
  private static final TokenSet ENABLED_TOKENS = TokenSet.create(XmlElementType.XML_CDATA,
                                                                 XmlTokenType.XML_DATA_CHARACTERS);
  @Override
  public boolean isWordCompletionEnabledIn(final IElementType element) {
    return super.isWordCompletionEnabledIn(element) || ENABLED_TOKENS.contains(element);
  }
}