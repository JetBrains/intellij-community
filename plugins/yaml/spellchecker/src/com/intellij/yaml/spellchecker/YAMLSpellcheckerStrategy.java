// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.yaml.spellchecker;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.yaml.YAMLSchemaNameMatcher;
import org.jetbrains.yaml.YAMLTokenTypes;
import org.jetbrains.yaml.psi.YAMLQuotedText;
import org.jetbrains.yaml.psi.impl.YAMLAnchorImpl;

import java.util.regex.Pattern;

public final class YAMLSpellcheckerStrategy extends SpellcheckingStrategy implements DumbAware {

  private static final Pattern CODE_LIKE_PATTERN = Pattern.compile("[\"']?" + CODE_IDENTIFIER_LIKE + "[\"']?");

  private final Tokenizer<YAMLQuotedText> myQuotedTextTokenizer = new Tokenizer<>() {
    @Override
    public void tokenize(@NotNull YAMLQuotedText element, @NotNull TokenConsumer consumer) {
      TextRange range = ElementManipulators.getValueTextRange(element);
      if (!range.isEmpty()) {
        String text = ElementManipulators.getValueText(element);
        consumer.consumeToken(element, text, false, range.getStartOffset(), TextRange.allOf(text), PlainTextSplitter.getInstance());
      }
    }
  };

  @Override
  public @NotNull Tokenizer<?> getTokenizer(PsiElement element) {
    final ASTNode node = element.getNode();
    if (node != null) {
      IElementType type = node.getElementType();
      if (type == YAMLTokenTypes.SCALAR_TEXT ||
          type == YAMLTokenTypes.SCALAR_LIST ||
          type == YAMLTokenTypes.TEXT ||
          type == YAMLTokenTypes.SCALAR_KEY ||
          type == YAMLTokenTypes.COMMENT) {
        if (useTextLevelSpellchecking()) {
          return EMPTY_TOKENIZER;
        }

        if (isInjectedLanguageFragment(element.getParent())) {
          return EMPTY_TOKENIZER;
        }

        if (YAMLSchemaNameMatcher.matchesNameFromSchema(element)) {
          return EMPTY_TOKENIZER;
        }

        return TEXT_TOKENIZER;
      } else if (element instanceof YAMLQuotedText) {
        if (useTextLevelSpellchecking()) {
          return EMPTY_TOKENIZER;
        }
        if (isInjectedLanguageFragment(element)) {
          return EMPTY_TOKENIZER;
        }

        if (YAMLSchemaNameMatcher.matchesNameFromSchema(element)) {
          return EMPTY_TOKENIZER;
        }

        return myQuotedTextTokenizer;
      }
    }

    if (shouldBeIgnored(element)) {
      return EMPTY_TOKENIZER;
    }
    return super.getTokenizer(element);
  }

  @Override
  protected boolean isLiteral(@NotNull PsiElement element) {
    return super.isLiteral(element) || !super.isComment(element) && !CODE_LIKE_PATTERN.matcher(element.getText()).matches();
  }

  @Override
  public boolean useTextLevelSpellchecking() {
    return Registry.is("spellchecker.grazie.enabled", false);
  }

  private boolean shouldBeIgnored(@NotNull PsiElement element) {
    if (element.getNode() == null) {
      return false;
    }
    IElementType type = element.getNode().getElementType();
    return useTextLevelSpellchecking() &&
           (type == YAMLTokenTypes.SCALAR_DSTRING || type == YAMLTokenTypes.ANCHOR || element instanceof YAMLAnchorImpl);
  }
}
