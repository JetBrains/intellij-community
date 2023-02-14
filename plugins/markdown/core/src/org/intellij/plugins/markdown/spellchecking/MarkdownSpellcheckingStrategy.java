package org.intellij.plugins.markdown.spellchecking;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.TokenSet;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.jetbrains.annotations.NotNull;

public class MarkdownSpellcheckingStrategy extends SpellcheckingStrategy {

  public static final TokenSet NO_SPELLCHECKING_TYPES = TokenSet.create(MarkdownElementTypes.CODE_BLOCK,
                                                                        MarkdownElementTypes.CODE_FENCE,
                                                                        MarkdownElementTypes.CODE_SPAN,
                                                                        MarkdownElementTypes.LINK_DESTINATION);

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    final ASTNode node = element.getNode();
    if (node == null || node.getElementType() != MarkdownTokenTypes.TEXT) {
      return EMPTY_TOKENIZER;
    }
    if (TreeUtil.findParent(node, NO_SPELLCHECKING_TYPES) != null) {
      return EMPTY_TOKENIZER;
    }

    return TEXT_TOKENIZER;
  }
}
