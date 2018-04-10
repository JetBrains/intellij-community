/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

/**
 * @author max
 */
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

  @Nullable
  @Override
  public CommenterDataHolder createLineCommentingState(int startLine, int endLine, @NotNull Document document, @NotNull PsiFile file) {
    return null;
  }

  @Nullable
  @Override
  public CommenterDataHolder createBlockCommentingState(int selectionStart,
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

  @Nullable
  @Override
  public String getCommentPrefix(int line, @NotNull Document document, @NotNull CommenterDataHolder data) {
    return HASH_COMMENT_PREFIX;
  }

  @Nullable
  @Override
  public TextRange getBlockCommentRange(int selectionStart,
                                        int selectionEnd,
                                        @NotNull Document document,
                                        @NotNull CommenterDataHolder data) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public String getBlockCommentPrefix(int selectionStart, @NotNull Document document, @NotNull CommenterDataHolder data) {
    return getBlockCommentPrefix();
  }

  @Nullable
  @Override
  public String getBlockCommentSuffix(int selectionEnd, @NotNull Document document, @NotNull CommenterDataHolder data) {
    return getBlockCommentSuffix();
  }

  @Override
  public void uncommentBlockComment(int startOffset, int endOffset, Document document, CommenterDataHolder data) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public TextRange insertBlockComment(int startOffset, int endOffset, Document document, CommenterDataHolder data) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  @Override
  public IElementType getLineCommentTokenType() {
    return PropertiesTokenTypes.END_OF_LINE_COMMENT;
  }

  @Nullable
  @Override
  public IElementType getBlockCommentTokenType() {
    return null;
  }

  @Nullable
  @Override
  public IElementType getDocumentationCommentTokenType() {
    return null;
  }

  @Nullable
  @Override
  public String getDocumentationCommentPrefix() {
    return null;
  }

  @Nullable
  @Override
  public String getDocumentationCommentLinePrefix() {
    return null;
  }

  @Nullable
  @Override
  public String getDocumentationCommentSuffix() {
    return null;
  }

  @Override
  public boolean isDocumentationComment(PsiComment element) {
    return false;
  }
}
