// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.properties.spellchecker;

import com.intellij.lang.properties.psi.Property;
import com.intellij.lang.properties.psi.impl.PropertyKeyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.inspections.PropertiesSplitter;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.intellij.spellchecker.tokenizer.TokenizerBase;
import org.jetbrains.annotations.NotNull;


public class PropertiesSpellcheckingStrategy extends SpellcheckingStrategy implements DumbAware {

  private final Tokenizer<PropertyValueImpl> myPropertyValueTokenizer = TokenizerBase.create(PlainTextSplitter.getInstance());
  private final Tokenizer<PropertyKeyImpl> myPropertyTokenizer = TokenizerBase.create(PropertiesSplitter.getInstance());

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
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
}
