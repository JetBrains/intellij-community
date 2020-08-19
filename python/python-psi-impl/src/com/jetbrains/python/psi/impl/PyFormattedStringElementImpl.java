// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFStringFragment;
import com.jetbrains.python.psi.PyFormattedStringElement;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class PyFormattedStringElementImpl extends PyElementImpl implements PyFormattedStringElement {

  public PyFormattedStringElementImpl(ASTNode astNode) {
    super(astNode);
  }

  @Override
  protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
    pyVisitor.visitPyFormattedStringElement(this);
  }

  @NotNull
  @Override
  public List<PyFStringFragment> getFragments() {
    return findChildrenByType(PyElementTypes.FSTRING_FRAGMENT);
  }

  @NotNull
  @Override
  public List<TextRange> getLiteralPartRanges() {
    final List<PsiElement> textTokens = findChildrenByType(PyTokenTypes.FSTRING_TEXT_TOKENS);
    return ContainerUtil.map(textTokens, PsiElement::getTextRangeInParent);
  }

  @NotNull
  @Override
  public String getPrefix() {
    return PyStringLiteralUtil.getPrefix(getText());
  }

  @Override
  public int getPrefixLength() {
    return PyStringLiteralUtil.getPrefixLength(getText());
  }

  @NotNull
  @Override
  public TextRange getContentRange() {
    final TextRange textRange = getTextRange();
    final int startOffset = textRange.getStartOffset();
    final int endOffset = textRange.getEndOffset();
    
    final PsiElement startToken = findNotNullChildByType(PyTokenTypes.FSTRING_START);
    final PsiElement endToken = findChildByType(PyTokenTypes.FSTRING_END);
    final TextRange absoluteRange = TextRange.create(startToken.getTextRange().getEndOffset(),
                                                     endToken != null ? endToken.getTextRange().getStartOffset() : endOffset);
    return absoluteRange.shiftLeft(startOffset);
  }

  @NotNull
  @Override
  public String getContent() {
    return getContentRange().substring(getText());
  }

  @NotNull
  @Override
  public List<Pair<TextRange, String>> getDecodedFragments() {
    final ArrayList<Pair<TextRange, String>> result = new ArrayList<>();
    final PyStringLiteralDecoder decoder = new PyStringLiteralDecoder(this);
    for (PsiElement child = getFirstChild(); child != null; child = child.getNextSibling()) {
      final IElementType childType = child.getNode().getElementType();
      final TextRange relChildRange = child.getTextRangeInParent();
      if (PyTokenTypes.FSTRING_TEXT_TOKENS.contains(childType)) {
        result.addAll(decoder.decodeRange(relChildRange));
      }
      else if (childType == PyElementTypes.FSTRING_FRAGMENT) {
        // There shouldn't be any escaping inside interpolated parts
        result.add(Pair.create(relChildRange, child.getText()));
      }
    }
    return result;
  }
  
  @NotNull
  @Override
  public String getQuote() {
    final PsiElement start = findNotNullChildByType(PyTokenTypes.FSTRING_START);
    return start.getText().substring(getPrefixLength());
  }

  @Override
  public boolean isTripleQuoted() {
    return getQuote().length() == 3;
  }

  @Override
  public boolean isTerminated() {
    return findChildrenByType(PyTokenTypes.FSTRING_END) != null;
  }

  @Override
  public boolean isUnicode() {
    return StringUtil.containsIgnoreCase(getPrefix(), "u");
  }

  @Override
  public boolean isRaw() {
    return StringUtil.containsIgnoreCase(getPrefix(), "r");
  }

  @Override
  public boolean isBytes() {
    return StringUtil.containsIgnoreCase(getPrefix(), "b");
  }

  @Override
  public final boolean isFormatted() {
    return true;
  }
}
