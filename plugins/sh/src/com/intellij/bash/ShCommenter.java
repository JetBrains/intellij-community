package com.intellij.bash;

import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.psi.PsiComment;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

import static com.intellij.bash.lexer.ShTokenTypes.COMMENT;

public class ShCommenter implements CodeDocumentationAwareCommenter {
  public String getLineCommentPrefix() {
    return "#";
  }

  public String getBlockCommentPrefix() {
    return "#";
  }

  public String getBlockCommentSuffix() {
    return null;
  }

  public String getCommentedBlockCommentPrefix() {
    return "#";
  }

  public String getCommentedBlockCommentSuffix() {
    return null;
  }

  @Nullable
  public IElementType getLineCommentTokenType() {
    return COMMENT;
  }

  @Nullable
  public IElementType getBlockCommentTokenType() {
    return null;
  }

  @Nullable
  public IElementType getDocumentationCommentTokenType() {
    return null;
  }

  @Nullable
  public String getDocumentationCommentPrefix() {
    return "#";
  }

  @Nullable
  public String getDocumentationCommentLinePrefix() {
    return "#";
  }

  @Nullable
  public String getDocumentationCommentSuffix() {
    return null;
  }

  public boolean isDocumentationComment(PsiComment element) {
    return false;
  }
}
