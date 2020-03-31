package org.jetbrains.plugins.textmate.editor;

import com.intellij.codeInsight.highlighting.BraceMatcher;
import com.intellij.codeInsight.highlighting.BraceMatchingUtil;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TextMateBraceMatcher implements BraceMatcher {

  @Override
  public int getBraceTokenGroupId(IElementType tokenType) {
    return BraceMatchingUtil.UNDEFINED_TOKEN_GROUP;
  }

  @Override
  public boolean isLBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
    if (iterator.getStart() == iterator.getEnd()) return false;
    IElementType tokenType = iterator.getTokenType();
    String currentSelector = tokenType != null ? tokenType.toString() : null;
    return TextMateEditorUtils.getHighlightingPairForLeftChar(fileText.charAt(iterator.getStart()), currentSelector) != null;
  }

  @Override
  public boolean isRBraceToken(HighlighterIterator iterator, CharSequence fileText, FileType fileType) {
    int end = iterator.getEnd();
    if (end == 0 || end == iterator.getStart()) return false;

    IElementType tokenType = iterator.getTokenType();
    String currentSelector = tokenType != null ? tokenType.toString() : null;
    return TextMateEditorUtils.getHighlightingPairForRightChar(fileText.charAt(end - 1), currentSelector) != null;
  }

  @Override
  public boolean isPairBraces(IElementType tokenType, IElementType tokenType2) {
    return true;
  }

  @Override
  public boolean isStructuralBrace(HighlighterIterator iterator, CharSequence text, FileType fileType) {
    return false;
  }

  @Nullable
  @Override
  public IElementType getOppositeBraceTokenType(@NotNull IElementType type) {
    return null;
  }

  @Override
  public boolean isPairedBracesAllowedBeforeType(@NotNull IElementType lbraceType, @Nullable IElementType contextType) {
    return true;
  }

  @Override
  public int getCodeConstructStart(PsiFile file, int openingBraceOffset) {
    return openingBraceOffset;
  }
}
