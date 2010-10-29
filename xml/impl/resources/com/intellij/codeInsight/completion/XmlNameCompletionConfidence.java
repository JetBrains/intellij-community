package com.intellij.codeInsight.completion;

import com.intellij.lang.ASTNode;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class XmlNameCompletionConfidence extends CompletionConfidence{
  @Override
  public Boolean shouldFocusLookup(@NotNull CompletionParameters parameters) {
    final ASTNode node = parameters.getPosition().getNode();
    if (node == null) return null;

    final IElementType elementType = node.getElementType();
    if (elementType == XmlTokenType.XML_NAME || elementType == XmlTokenType.XML_TAG_NAME) {
      return true;
    }
    return null;
  }
}
