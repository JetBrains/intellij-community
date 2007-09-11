package com.intellij.ide.highlighter.custom.impl;

import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.tree.IElementType;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Dec 6, 2004
 * Time: 8:36:58 PM
 * To change this template use File | Settings | File Templates.
 */
class CustomFileTypeBraceMatcher implements BraceMatchingUtil.BraceMatcher {
  public int getTokenGroup(IElementType tokenType) {
    return 777;
  }

  public boolean isLBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
    final IElementType tokenType = iterator.getTokenType();

    return tokenType == CustomHighlighterTokenType.L_BRACKET ||
           tokenType == CustomHighlighterTokenType.L_PARENTH ||
           tokenType == CustomHighlighterTokenType.L_BRACE;
  }

  public boolean isRBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
    final IElementType tokenType = iterator.getTokenType();

    return tokenType == CustomHighlighterTokenType.R_BRACKET ||
           tokenType == CustomHighlighterTokenType.R_PARENTH ||
           tokenType == CustomHighlighterTokenType.R_BRACE;
  }

  public boolean isPairBraces(IElementType tokenType, IElementType tokenType2) {
    return (tokenType == CustomHighlighterTokenType.L_BRACE && tokenType2 == CustomHighlighterTokenType.R_BRACE) ||
           (tokenType == CustomHighlighterTokenType.R_BRACE && tokenType2 == CustomHighlighterTokenType.L_BRACE) ||
           (tokenType == CustomHighlighterTokenType.L_BRACKET && tokenType2 == CustomHighlighterTokenType.R_BRACKET) ||
           (tokenType == CustomHighlighterTokenType.R_BRACKET && tokenType2 == CustomHighlighterTokenType.L_BRACKET) ||
           (tokenType == CustomHighlighterTokenType.L_PARENTH && tokenType2 == CustomHighlighterTokenType.R_PARENTH) ||
           (tokenType == CustomHighlighterTokenType.R_PARENTH && tokenType2 == CustomHighlighterTokenType.L_PARENTH);
  }

  public boolean isStructuralBrace(HighlighterIterator iterator, CharSequence text, FileType fileType) {
    final IElementType type = iterator.getTokenType();
    return type == CustomHighlighterTokenType.L_BRACE || type == CustomHighlighterTokenType.R_BRACE;
  }

  public IElementType getTokenType(char ch, HighlighterIterator iterator) {
    if (iterator.atEnd() || !(iterator.getTokenType() instanceof CustomHighlighterTokenType.CustomElementType)) {
      return null;
    }

    if (ch == '}') return CustomHighlighterTokenType.R_BRACE;
    if (ch == '{') return CustomHighlighterTokenType.L_BRACE;

    if (ch == ']') return CustomHighlighterTokenType.R_BRACKET;
    if (ch == '[') return CustomHighlighterTokenType.L_BRACKET;
    if (ch == ')') return CustomHighlighterTokenType.R_PARENTH;
    if (ch == '(') return CustomHighlighterTokenType.L_PARENTH;

    return null;
  }
}
