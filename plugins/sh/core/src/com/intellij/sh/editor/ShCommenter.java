// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.sh.editor;

import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.psi.PsiComment;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

import static com.intellij.sh.lexer.ShTokenTypes.COMMENT;

public class ShCommenter implements CodeDocumentationAwareCommenter {
  @Override
  public String getLineCommentPrefix() {
    return "#";
  }

  @Override
  public String getBlockCommentPrefix() {
    return "#";
  }

  @Override
  public String getBlockCommentSuffix() {
    return null;
  }

  @Override
  public String getCommentedBlockCommentPrefix() {
    return "#";
  }

  @Override
  public String getCommentedBlockCommentSuffix() {
    return null;
  }

  @Override
  @Nullable
  public IElementType getLineCommentTokenType() {
    return COMMENT;
  }

  @Override
  @Nullable
  public IElementType getBlockCommentTokenType() {
    return null;
  }

  @Override
  @Nullable
  public IElementType getDocumentationCommentTokenType() {
    return null;
  }

  @Override
  @Nullable
  public String getDocumentationCommentPrefix() {
    return "#";
  }

  @Override
  @Nullable
  public String getDocumentationCommentLinePrefix() {
    return "#";
  }

  @Override
  @Nullable
  public String getDocumentationCommentSuffix() {
    return null;
  }

  @Override
  public boolean isDocumentationComment(PsiComment element) {
    return false;
  }
}
