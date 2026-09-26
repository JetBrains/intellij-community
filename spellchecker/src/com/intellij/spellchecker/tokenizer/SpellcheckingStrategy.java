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
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiPlainText;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Defines spellchecking support for a custom programming language.
 * <p>
 * Register via extension point {@code com.intellij.spellchecker.support}
 * and override {@link #getTokenizer(PsiElement)} to skip/handle specific code elements.
 * <p>
 * To define spellchecking support for non-code-like texts, read the documentation of {@link SpellcheckingStrategy#useTextLevelSpellchecking()}
 * <p>
 * Mark your strategy as {@link com.intellij.openapi.project.DumbAware} if it does not need indexes to perform.
 * <p>
 */
public class SpellcheckingStrategy implements PossiblyDumbAware {
  // Consider literals that look like typical programming language identifier to be code contexts
  protected static final Pattern CODE_IDENTIFIER_LIKE = Pattern.compile("([a-zA-Z][a-zA-Z0-9_]*)");

  protected final Tokenizer<PsiComment> myCommentTokenizer = new CommentTokenizer();
  private static final int SCOPE_COUNT = SpellCheckingInspection.SpellCheckingScope.values().length;

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
   * Defines {@link Tokenizer} to handle spellchecking for code constructs (methods, classes, variables etc.).
   * Use {@link #EMPTY_TOKENIZER} to skip spellchecking, {@link #TEXT_TOKENIZER} for full element text or custom Tokenizer implementation.
   * For text fragments in natural language, see {@link #useTextLevelSpellchecking()}
   */
  public @NotNull Tokenizer getTokenizer(PsiElement element) {
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
    if (scope.size() == SCOPE_COUNT) return true;
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
   * Defines if the strategy should delegate spellchecking of non-code like fragments to text level approach for the programming language.
   *
   * <h2>Text-level spellchecking</h2>
   *
   * <p>The strategy works with individual words split by CamelCase heuristics.
   * While simple, this approach often causes false positives in technical contexts.</p>
   *
   * <p>Text-level spellchecking addresses these issues by utilizing text tokenization and
   * analyzing the text as a whole before processing, leading to better results overall.</p>
   *
   * <h2>Migration Guide</h2>
   *
   * <h3>1. Enable text-level checking</h3>
   * The strategy must override this method to return {@code true}. It is recommended to keep it under existing registry key,
   * which is enabled by default:
   * <pre>{@code
   * @Override
   * public boolean useTextLevelSpellchecking() {
   *     return Registry.is("spellchecker.grazie.enabled", false);
   * }
   * }</pre>
   *
   * <h3>2. Ensure you have a {@code TextExtractor} for your language</h3>
   *
   * <h3>3. Update the strategy</h3>
   * Ensure that the strategy returns the {@link #EMPTY_TOKENIZER} from {@link #getTokenizer(PsiElement)}
   * for text-containing elements
   *
   * @see <a href="https://github.com/JetBrains/intellij-community/commit/24b3c418df8aa8ca6659c89a580753dad9871ee1">Kotlin Migration Example</a>
   * @see <a href="https://github.com/JetBrains/intellij-community/commit/3b26b8c52ff0483bf76ed7bdea47177afef37978">Python Migration Example</a>
   * @see <a href="https://github.com/JetBrains/intellij-community/commit/c5d3c3b87e3d75eafca7a1d503ce33977e91d803">Removing {@code @author} false positives</a>
   */
  public boolean useTextLevelSpellchecking() {
    return false;
  }

  /**
   * Defines if the strategy should delegate spellchecking of given {@link PsiElement} to text-level approach.
   * <p>
   * This method was introduced to support text-level checking in complicated cases when one file
   * contains multiple languages at the same time. The most common case is of combination of JS / XML / HTML / Vue in a single file.
   * <p>
   * In simple cases, this method shouldn't be overridden.
   * <p>
   * If {@link SpellcheckingStrategy#useTextLevelSpellchecking()} returns false, this method must always return false too.
   */
  public boolean useTextLevelSpellchecking(PsiElement element) {
    return useTextLevelSpellchecking();
  }

  /**
   * Returns the file-relative range of the identifier text that should participate in the rename quick fix.
   * <p>
   * {@code nameIdentifier} is the PSI element used as the rename anchor, not an arbitrary descendant.
   * <p>
   * {@link PsiNameIdentifierOwner#getNameIdentifier()} of the enclosing {@link PsiNamedElement} when it is available;
   * otherwise it falls back to the original PSI element on which spellchecking reported the typo.
   * <p>
   * The range is expected to cover the full logical name, even if the underlying PSI element contains
   * language-specific symbols / constructs or non-editable fragments. For example, in PHP for the element {@code $lengthMinutes} the method should
   * return the range of {@code lengthMinutes}, i.e. {@code [1; 14]}.
   * <p>
   * Return {@code null} when the strategy cannot determine such a range for the supplied rename anchor.
   */
  @ApiStatus.Experimental
  public @Nullable TextRange getRenameIdentifierRange(@NotNull PsiElement nameIdentifier) {
    return null;
  }

  protected static boolean isInjectedLanguageFragment(@Nullable PsiElement element) {
    return element instanceof PsiLanguageInjectionHost
           && InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)element);
  }

  // Used by 3rd party plugins
  @SuppressWarnings("unused")
  public LocalQuickFix[] getRegularFixes(@NotNull PsiElement element,
                                         @NotNull TextRange textRange,
                                         boolean useRename,
                                         String typo) {
    return getDefaultRegularFixes(useRename, typo, element, textRange, null);
  }

  public LocalQuickFix[] getRegularFixes(@NotNull PsiElement element,
                                         @NotNull TextRange textRange,
                                         boolean useRename,
                                         String typo,
                                         @Nullable Set<String> suggestions) {
    return getDefaultRegularFixes(useRename, typo, element, textRange, suggestions);
  }

  public static SpellcheckingStrategy getSpellcheckingStrategy(@NotNull PsiElement element) {
    DumbService dumbService = DumbService.getInstance(element.getProject());
    for (SpellcheckingStrategy strategy : LanguageSpellchecking.INSTANCE.allForLanguage(element.getLanguage())) {
      if (dumbService.isUsableInCurrentContext(strategy) && strategy.isMyContext(element)) {
        return strategy;
      }
    }
    return null;
  }

  public static LocalQuickFix[] getDefaultRegularFixes(boolean useRename,
                                                       String typo,
                                                       @NotNull PsiElement element,
                                                       @NotNull TextRange range,
                                                       @Nullable Set<String> suggestions) {
    List<LocalQuickFix> result = new ArrayList<>();
    SpellcheckerRateTracker tracker = new SpellcheckerRateTracker(element);

    if (useRename && PsiTreeUtil.getNonStrictParentOfType(element, PsiNamedElement.class) != null) {
      result.add(SpellCheckerQuickFixFactory.rename(typo, range, element, tracker));
    } else {
      result.addAll(SpellCheckerQuickFixFactory.changeToVariants(element, range, typo, tracker, suggestions));
      result.addAll(SpellCheckerQuickFixFactory.additionalFixes());
    }

    SpellCheckerSettings settings = SpellCheckerSettings.getInstance(element.getProject());
    DictionaryLayer layer = null;
    if (settings.isUseSingleDictionaryToSave()) {
      layer = DictionaryLayersProvider.getLayer(element.getProject(), settings.getDictionaryToSave());
    }
    result.add(SpellCheckerQuickFixFactory.saveTo(element, range, typo, layer, tracker));
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
