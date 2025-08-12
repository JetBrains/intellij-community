// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.spellchecker;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.inspections.PropertiesSplitter;
import com.intellij.spellchecker.inspections.Splitter;
import com.intellij.spellchecker.tokenizer.*;
import org.jetbrains.annotations.NotNull;

final class PropertiesSpellcheckingStrategy extends SpellcheckingStrategy implements DumbAware {

  private static final ExtensionPointName<MnemonicsTokenizer> MNEMONICS_EP_NAME =
    ExtensionPointName.create("com.intellij.properties.spellcheckerMnemonicsTokenizer");

  private final Tokenizer<PropertyValueImpl> myPropertyValueTokenizer = new PropertyValueTokenizer();
  private final Tokenizer<PropertyKeyImpl> myPropertyTokenizer = new PropertyKeyTokenizer();

  @Override
  public @NotNull Tokenizer<?> getTokenizer(PsiElement element) {
    if (isInjectedLanguageFragment(element)) {
      return EMPTY_TOKENIZER;
    }
    if (element instanceof PropertyValueImpl) {
      return myPropertyValueTokenizer;
    }
    if (element instanceof PropertyKeyImpl) {
      return myPropertyTokenizer;
    }
    if (element instanceof Property) {
      return EMPTY_TOKENIZER;
    }
    return super.getTokenizer(element);
  }

  @Override
  public boolean useTextLevelSpellchecking() {
    return Registry.is("spellchecker.grazie.enabled");
  }

  @Override
  protected boolean isLiteral(@NotNull PsiElement element) {
    return !super.isComment(element);
  }

  private static class PropertyKeyTokenizer extends TokenizerBase<PropertyKeyImpl> {
    private PropertyKeyTokenizer() {
      super(PropertiesSplitter.getInstance());
    }

    @Override
    public void consumeToken(@NotNull PropertyKeyImpl element, @NotNull TokenConsumer consumer, @NotNull Splitter splitter) {
      consumer.consumeToken(element, true, splitter);
    }
  }

  private static class PropertyValueTokenizer extends EscapeSequenceTokenizer<PropertyValueImpl> {
    @Override
    public void tokenize(@NotNull PropertyValueImpl element, @NotNull TokenConsumer consumer) {
      String text = element.getText();

      for (MnemonicsTokenizer tokenizer : MNEMONICS_EP_NAME.getExtensionList()) {
        if (tokenizer.hasMnemonics(text)) {
          tokenizer.tokenize(element, consumer);
          return;
        }
      }

      if (!text.contains("\\")) {
        consumer.consumeToken(element, PlainTextSplitter.getInstance());
      }
      else {
        StringBuilder unescapedText = new StringBuilder(text.length());
        int[] offsets = new int[text.length() + 1];
        CodeInsightUtilCore.parseStringCharacters(text, unescapedText, offsets);

        processTextWithOffsets(element, consumer, unescapedText, offsets, 0);
      }
    }
  }
}
