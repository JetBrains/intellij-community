/*
 * @author max
 */
package com.intellij.lang.html;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

public class HTMLParser implements PsiParser {

  @NotNull
  public ASTNode parse(final IElementType root, final PsiBuilder builder) {
    builder.enforceCommentTokens(TokenSet.EMPTY);
    final PsiBuilder.Marker file = builder.mark();
    new HtmlParsing(builder).parseDocument();
    file.done(root);
    return builder.getTreeBuilt();
  }

}