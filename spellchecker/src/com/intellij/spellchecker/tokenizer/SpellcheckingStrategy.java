// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.tokenizer;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.spellchecker.DictionaryLayer;
import com.intellij.spellchecker.DictionaryLayersProvider;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.inspections.SpellCheckingInspection;
import com.intellij.spellchecker.quickfixes.SpellCheckerQuickFixFactory;
import com.intellij.spellchecker.settings.SpellCheckerSettings;
import com.intellij.spellchecker.statistics.SpellcheckerRateTracker;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Defines spellchecking support for a custom language.
 * <p>
 * Register via extension point {@code com.intellij.spellchecker.support}
 * and override {@link #getTokenizer(PsiElement)} to skip/handle specific elements.
 * <p>
 * Mark your strategy as {@link com.intellij.openapi.project.DumbAware} if it does not need indexes to perform
 */
public class SpellcheckingStrategy implements PossiblyDumbAware {
  // Consider literals that look like typical programming language identifier to be code contexts
  protected static final Pattern CODE_IDENTIFIER_LIKE = Pattern.compile("([a-zA-Z][a-zA-Z0-9_]*)");

  protected final Tokenizer<PsiComment> myCommentTokenizer = new CommentTokenizer();

  public static final ExtensionPointName<KeyedLazyInstance<SpellcheckingStrategy>> EP_NAME =
    new ExtensionPointName<>("com.intellij.spellchecker.support");
  public static final Tokenizer EMPTY_TOKENIZER = new Tokenizer() {
    @Override
    public void tokenize(@NotNull PsiElement element, @NotNull TokenConsumer consumer) {
    }

    @Override
    public String toString() {
      return "EMPTY_TOKENIZER";
    }
  };

  public static final Tokenizer<PsiElement> TEXT_TOKENIZER = new TokenizerBase<>(PlainTextSplitter.getInstance());

  /**
   * @see SpellcheckingStrategy#EMPTY_TOKENIZER
   */
  public @NotNull Tokenizer getTokenizer(@NotNull PsiElement element, @NotNull Set<SpellCheckingInspection.SpellCheckingScope> scope) {
    return getTokenizer(element);
  }

  /**
   * @return {@link #EMPTY_TOKENIZER} to skip spellchecking, {@link #TEXT_TOKENIZER} for full element text or custom Tokenizer implementation.
   */
  public @NotNull Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof PsiWhiteSpace) {
      return EMPTY_TOKENIZER;
    }
    if (isInjectedLanguageFragment(element)) {
      return EMPTY_TOKENIZER;
    }
    if (element instanceof PsiNameIdentifierOwner) return PsiIdentifierOwnerTokenizer.INSTANCE;
    if (element instanceof PsiComment) {
      if (SuppressionUtil.isSuppressionComment(element)) {
        return EMPTY_TOKENIZER;
      }
      //don't check shebang
      if (element.getTextOffset() == 0 && element.getText().startsWith("#!")) {
        return EMPTY_TOKENIZER;
      }
      return myCommentTokenizer;
    }
    if (element instanceof PsiPlainText) {
      PsiFile file = element.getContainingFile();
      FileType fileType = file == null ? null : file.getFileType();
      if (fileType instanceof CustomSyntaxTableFileType) {
        return new CustomFileTypeTokenizer(((CustomSyntaxTableFileType)fileType).getSyntaxTable());
      }
      return TEXT_TOKENIZER;
    }
    return EMPTY_TOKENIZER;
  }

  public boolean elementFitsScope(@NotNull PsiElement element, Set<SpellCheckingInspection.SpellCheckingScope> scope) {
    Language language = element.getLanguage();
    ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(language);

    if (parserDefinition != null) {
      if (isLiteral(element)) {
        if (!scope.contains(SpellCheckingInspection.SpellCheckingScope.Literals)) {
          return false;
        }
      }
      else if (isComment(element)) {
        if (!scope.contains(SpellCheckingInspection.SpellCheckingScope.Comments)) {
          return false;
        }
      }
      else if (!scope.contains(SpellCheckingInspection.SpellCheckingScope.Code)) {
        return false;
      }
    }
    return true;
  }

  protected boolean isLiteral(@NotNull PsiElement psiElement) {
    ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(psiElement.getLanguage());
    return parserDefinition.getStringLiteralElements().contains(psiElement.getNode().getElementType());
  }

  protected boolean isComment(@NotNull PsiElement psiElement) {
    ParserDefinition parserDefinition = LanguageParserDefinitions.INSTANCE.forLanguage(psiElement.getLanguage());
    return parserDefinition.getCommentTokens().contains(psiElement.getNode().getElementType());
  }


  /**
   * Controls whether to use text-level spellchecking provided by {@link com.intellij.grazie.spellcheck.GrazieSpellcheckingExtension}.
   */
  public boolean useTextLevelSpellchecking() {
    return false;
  }

  protected static boolean isInjectedLanguageFragment(@Nullable PsiElement element) {
    return element instanceof PsiLanguageInjectionHost
           && InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)element);
  }

  public LocalQuickFix[] getRegularFixes(@NotNull PsiElement element,
                                         @NotNull TextRange textRange,
                                         boolean useRename,
                                         String typo) {
    return getDefaultRegularFixes(useRename, typo, element, textRange);
  }

  public static LocalQuickFix[] getDefaultRegularFixes(boolean useRename,
                                                       String typo,
                                                       @NotNull PsiElement element,
                                                       @NotNull TextRange range) {
    ArrayList<LocalQuickFix> result = new ArrayList<>();
    SpellcheckerRateTracker tracker = new SpellcheckerRateTracker(element);

    if (useRename && PsiTreeUtil.getNonStrictParentOfType(element, PsiNamedElement.class) != null) {
      result.add(SpellCheckerQuickFixFactory.rename(element, tracker));
    } else {
      List<LocalQuickFix> fixes = SpellCheckerQuickFixFactory.changeToVariants(element, range, typo, tracker);
      result.addAll(fixes);
    }

    final SpellCheckerSettings settings = SpellCheckerSettings.getInstance(element.getProject());
    if (settings.isUseSingleDictionaryToSave()) {
      DictionaryLayer layer = DictionaryLayersProvider.getLayer(element.getProject(), settings.getDictionaryToSave());
      result.add(SpellCheckerQuickFixFactory.saveTo(element, range, typo, layer, tracker));
      return result.toArray(LocalQuickFix.EMPTY_ARRAY);
    }

    result.add(SpellCheckerQuickFixFactory.saveTo(element, range, typo, tracker));
    return result.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  public static LocalQuickFix[] getDefaultBatchFixes(
    @NotNull PsiElement element,
    @NotNull TextRange textRange,
    @NotNull String word
  ) {
    Collection<DictionaryLayer> layers = DictionaryLayersProvider.getAllLayers(element.getProject());
    SpellcheckerRateTracker tracker = new SpellcheckerRateTracker(element);
    return layers.stream()
      .map(it -> SpellCheckerQuickFixFactory.saveTo(element, textRange, word, it, tracker))
      .toArray(LocalQuickFix[]::new);
  }

  public boolean isMyContext(@NotNull PsiElement element) {
    return true;
  }
}
