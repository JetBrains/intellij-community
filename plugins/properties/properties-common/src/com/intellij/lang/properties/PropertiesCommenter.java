// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties;

import com.intellij.codeInsight.generation.CommenterDataHolder;
import com.intellij.codeInsight.generation.SelfManagingCommenter;
import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.lang.properties.parsing.PropertiesTokenTypes;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PropertiesCommenter implements CodeDocumentationAwareCommenter, SelfManagingCommenter<CommenterDataHolder> {
  public static final String HASH_COMMENT_PREFIX = "#";
  public static final String EXCLAMATION_COMMENT_PREFIX = "!";

  @Override
  public String getLineCommentPrefix() {
    return HASH_COMMENT_PREFIX;
  }

  @Override
  public String getBlockCommentPrefix() {
    return null;
  }

  @Override
  public String getBlockCommentSuffix() {
    return null;
  }

  @Override
  public String getCommentedBlockCommentPrefix() {
    return null;
  }

  @Override
  public String getCommentedBlockCommentSuffix() {
    return null;
  }

  @Override
  public @Nullable CommenterDataHolder createLineCommentingState(int startLine, int endLine, @NotNull Document document, @NotNull PsiFile file) {
    return null;
  }

  @Override
  public @Nullable CommenterDataHolder createBlockCommentingState(int selectionStart,
                                                                  int selectionEnd,
                                                                  @NotNull Document document,
                                                                  @NotNull PsiFile file) {
    return null;
  }

  @Override
  public void commentLine(int line, int offset, @NotNull Document document, @NotNull CommenterDataHolder data) {
    document.insertString(offset, HASH_COMMENT_PREFIX);
  }

  @Override
  public void uncommentLine(int line, int offset, @NotNull Document document, @NotNull CommenterDataHolder data) {
    document.deleteString(offset, offset + HASH_COMMENT_PREFIX.length());
  }

  @Override
  public boolean isLineCommented(int line, int offset, @NotNull Document document, @NotNull CommenterDataHolder data) {
    return CharArrayUtil.regionMatches(document.getCharsSequence(), offset, HASH_COMMENT_PREFIX) ||
           CharArrayUtil.regionMatches(document.getCharsSequence(), offset, EXCLAMATION_COMMENT_PREFIX);
  }

  @Override
  public @Nullable String getCommentPrefix(int line, @NotNull Document document, @NotNull CommenterDataHolder data) {
    return HASH_COMMENT_PREFIX;
  }

  @Override
  public @Nullable TextRange getBlockCommentRange(int selectionStart,
                                                  int selectionEnd,
                                                  @NotNull Document document,
                                                  @NotNull CommenterDataHolder data) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable String getBlockCommentPrefix(int selectionStart, @NotNull Document document, @NotNull CommenterDataHolder data) {
    return getBlockCommentPrefix();
  }

  @Override
  public @Nullable String getBlockCommentSuffix(int selectionEnd, @NotNull Document document, @NotNull CommenterDataHolder data) {
    return getBlockCommentSuffix();
  }

  @Override
  public void uncommentBlockComment(int startOffset, int endOffset, Document document, CommenterDataHolder data) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull TextRange insertBlockComment(int startOffset, int endOffset, Document document, CommenterDataHolder data) {
    throw new UnsupportedOperationException();
  }

  @Override
  public @Nullable IElementType getLineCommentTokenType() {
    return PropertiesTokenTypes.END_OF_LINE_COMMENT;
  }

  @Override
  public @Nullable IElementType getBlockCommentTokenType() {
    return null;
  }

  @Override
  public @Nullable IElementType getDocumentationCommentTokenType() {
    return null;
  }

  @Override
  public @Nullable String getDocumentationCommentPrefix() {
    return null;
  }

  @Override
  public @Nullable String getDocumentationCommentLinePrefix() {
    return null;
  }

  @Override
  public @Nullable String getDocumentationCommentSuffix() {
    return null;
  }

  @Override
  public boolean isDocumentationComment(PsiComment element) {
    return false;
  }
}
