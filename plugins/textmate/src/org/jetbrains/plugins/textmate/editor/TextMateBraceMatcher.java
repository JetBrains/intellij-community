package org.jetbrains.plugins.textmate.editor;

import com.intellij.codeInsight.highlighting.BraceMatcher;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateElementType;
import org.jetbrains.plugins.textmate.language.syntax.lexer.TextMateScope;

public class TextMateBraceMatcher implements BraceMatcher {

  @Override
  public int getBraceTokenGroupId(@NotNull IElementType tokenType) {
    return BraceMatchingUtil.UNDEFINED_TOKEN_GROUP;
  }

  @Override
  public boolean isLBraceToken(@NotNull HighlighterIterator iterator, @NotNull CharSequence fileText, @NotNull FileType fileType) {
    if (iterator.getStart() == iterator.getEnd()) return false;
    IElementType tokenType = iterator.getTokenType();
    TextMateScope currentSelector = tokenType instanceof TextMateElementType ? ((TextMateElementType)tokenType).getScope() : null;
    return TextMateEditorUtils.findRightHighlightingPair(iterator.getStart(), fileText, currentSelector) != null;
  }

  @Override
  public boolean isRBraceToken(@NotNull HighlighterIterator iterator, @NotNull CharSequence fileText, @NotNull FileType fileType) {
    if (iterator.getEnd() == iterator.getStart()) return false;
    IElementType tokenType = iterator.getTokenType();
    TextMateScope currentSelector = tokenType instanceof TextMateElementType ? ((TextMateElementType)tokenType).getScope() : null;
    return TextMateEditorUtils.findLeftHighlightingPair(iterator.getEnd(), fileText, currentSelector) != null;
  }

  @Override
  public boolean isPairBraces(@NotNull IElementType tokenType, @NotNull IElementType tokenType2) {
    return true;
  }

  @Override
  public boolean isStructuralBrace(@NotNull HighlighterIterator iterator, @NotNull CharSequence text, @NotNull FileType fileType) {
    return false;
  }

  @Override
  public @Nullable IElementType getOppositeBraceTokenType(@NotNull IElementType type) {
    return null;
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
    return true;
  }

  @Override
  public int getCodeConstructStart(@NotNull PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }
}
