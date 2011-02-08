package org.jetbrains.yaml.spellchecker;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLLanguage;
import org.jetbrains.yaml.YAMLTokenTypes;

/**
 * @author oleg
 */
public class YAMLSpellcheckerStrategy extends SpellcheckingStrategy {
  @NotNull
  @Override
  public Language getLanguage() {
    return YAMLLanguage.INSTANCE;
  }

  @NotNull
  @Override
  public Tokenizer getTokenizer(final PsiElement element) {
    final ASTNode node = element.getNode();
    if (node != null){
      final IElementType type = node.getElementType();
      if (type == YAMLTokenTypes.SCALAR_TEXT ||
          type == YAMLTokenTypes.SCALAR_STRING ||
          type == YAMLTokenTypes.SCALAR_DSTRING ||
          type == YAMLTokenTypes.COMMENT) {
        return SpellcheckingStrategy.TOKENIZER;
      }
    }
    return super.getTokenizer(element);
  }
}