// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static com.intellij.spellchecker.tokenizer.SpellcheckingStrategy.getSpellcheckingStrategy;

/**
 * Please use {@link com.intellij.grazie.spellcheck.GrazieSpellCheckingInspection} instead.
 * The class is not marked as deprecated even though it is not used as an inspection anymore
 * because {@link SpellCheckingScope} and both {@link SpellCheckingInspection#tokenize} methods are not deprecated and will not be in foreseeable future.
 * They should be present here to preserve backward compatibility.
 */
public abstract class SpellCheckingInspection extends LocalInspectionTool implements DumbAware {
  public static final String SPELL_CHECKING_INSPECTION_TOOL_NAME = "SpellCheckingInspection";

  /**
   * Splits element text in tokens according to spell checker strategy of given language
   *
   * @param element  Psi element
   * @param consumer the consumer of tokens
   */
  public static void tokenize(@NotNull PsiElement element,
                              TokenConsumer consumer, Set<SpellCheckingScope> allowedScopes) {
    SpellcheckingStrategy factoryByLanguage = getSpellcheckingStrategy(element);
    if (factoryByLanguage == null) {
      return;
    }
    tokenize(factoryByLanguage, element, consumer, allowedScopes);
  }

  public static void tokenize(SpellcheckingStrategy strategy,
                              PsiElement element,
                              TokenConsumer consumer,
                              Set<SpellCheckingScope> allowedScopes) {
    var tokenizer = strategy.getTokenizer(element, allowedScopes);
    //noinspection unchecked
    tokenizer.tokenize(element, consumer);
  }

  public enum SpellCheckingScope {
    Comments,
    Literals,
    Code,
  }
}
