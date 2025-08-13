package org.intellij.plugins.markdown.spellchecker;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.TreeUtil;
import com.intellij.psi.tree.TokenSet;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.intellij.plugins.markdown.lang.MarkdownElementTypes;
import org.intellij.plugins.markdown.lang.MarkdownTokenTypes;
import org.jetbrains.annotations.NotNull;

final class MarkdownSpellcheckingStrategy extends SpellcheckingStrategy implements DumbAware {
  public static final TokenSet NO_SPELLCHECKING_TYPES = TokenSet.create(
    MarkdownElementTypes.CODE_BLOCK,
    MarkdownElementTypes.CODE_FENCE,
    MarkdownElementTypes.CODE_SPAN,
    MarkdownElementTypes.LINK_DESTINATION
  );

  @Override
  public @NotNull Tokenizer getTokenizer(PsiElement element) {
    final ASTNode node = element.getNode();
    if (node == null || node.getElementType() != MarkdownTokenTypes.TEXT) {
      return EMPTY_TOKENIZER;
    }
    if (TreeUtil.findParent(node, NO_SPELLCHECKING_TYPES) != null) {
      return EMPTY_TOKENIZER;
    }

    return TEXT_TOKENIZER;
  }

  @Override
  public boolean useTextLevelSpellchecking() {
    return Registry.is("spellchecker.grazie.enabled");
  }
}
