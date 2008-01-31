package com.intellij.lang.java;

import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiComment;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class JavaCommenter implements CodeDocumentationAwareCommenter {

  public String getLineCommentPrefix() {
    return "//";
  }

  public boolean isLineCommentPrefixOnZeroColumn() {
    return false;
  }

  public String getBlockCommentPrefix() {
    return "/*";
  }

  public String getBlockCommentSuffix() {
    return "*/";
  }

  @Nullable
  public IElementType getLineCommentTokenType() {
    return JavaTokenType.END_OF_LINE_COMMENT;
  }

  @Nullable
  public IElementType getBlockCommentTokenType() {
    return JavaTokenType.C_STYLE_COMMENT;
  }

  @Nullable
  public IElementType getDocumentationCommentTokenType() {
    return JavaTokenType.DOC_COMMENT;
  }

  public String getDocumentationCommentPrefix() {
    return "/**";
  }

  public String getDocumentationCommentLinePrefix() {
    return "*";
  }

  public String getDocumentationCommentSuffix() {
    return "*/";
  }

  public boolean isDocumentationComment(final PsiComment element) {
    return element instanceof PsiDocComment;
  }
}
