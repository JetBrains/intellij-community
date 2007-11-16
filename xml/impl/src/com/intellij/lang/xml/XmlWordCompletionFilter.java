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
  private final static TokenSet ENABLED_TOKENS = TokenSet.create(XmlElementType.XML_CDATA,
                                                                 XmlTokenType.XML_ATTRIBUTE_VALUE_TOKEN,
                                                                 XmlTokenType.XML_DATA_CHARACTERS);
  public boolean isWordCompletionEnabledIn(final IElementType element) {
    return super.isWordCompletionEnabledIn(element) || ENABLED_TOKENS.contains(element);
  }
}