// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker.inspections;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.spellchecker.SpellCheckerManager;
import com.intellij.spellchecker.tokenizer.*;
import com.intellij.spellchecker.util.SpellCheckerBundle;
import com.intellij.util.Consumer;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class SpellCheckingInspection extends LocalInspectionTool implements DumbAware {
  public static final String SPELL_CHECKING_INSPECTION_TOOL_NAME = "SpellCheckingInspection";

  @Override
  public SuppressQuickFix @NotNull [] getBatchSuppressActions(@Nullable PsiElement element) {
    if (element != null) {
      final Language language = element.getLanguage();
      SpellcheckingStrategy strategy = getSpellcheckingStrategy(element, language);
      if (strategy instanceof SuppressibleSpellcheckingStrategy) {
        return ((SuppressibleSpellcheckingStrategy)strategy).getSuppressActions(element, getShortName());
      }
    }
    return super.getBatchSuppressActions(element);
  }

  private static SpellcheckingStrategy getSpellcheckingStrategy(@NotNull PsiElement element, @NotNull Language language) {
    DumbService dumbService = DumbService.getInstance(element.getProject());
    for (SpellcheckingStrategy strategy : LanguageSpellchecking.INSTANCE.allForLanguage(language)) {
      if (dumbService.isUsableInCurrentContext(strategy) && strategy.isMyContext(element)) {
        return strategy;
      }
    }
    return null;
  }

  @Override
  public boolean isSuppressedFor(@NotNull PsiElement element) {
    final Language language = element.getLanguage();
    SpellcheckingStrategy strategy = getSpellcheckingStrategy(element, language);
    if (strategy instanceof SuppressibleSpellcheckingStrategy) {
      return ((SuppressibleSpellcheckingStrategy)strategy).isSuppressedFor(element, getShortName());
    }
    return super.isSuppressedFor(element);
  }

  @Override
  public @NonNls @NotNull String getShortName() {
    return SPELL_CHECKING_INSPECTION_TOOL_NAME;
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    if (!Registry.is("spellchecker.inspection.enabled", true)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    final SpellCheckerManager manager = SpellCheckerManager.getInstance(holder.getProject());
    var scope = buildAllowedScopes();

    return new PsiElementVisitor() {
      @Override
      public void visitElement(final @NotNull PsiElement element) {
        if (holder.getResultCount() > 1000) return;

        final ASTNode node = element.getNode();
        if (node == null) {
          return;
        }

        final Language language = element.getLanguage();
        var strategy = getSpellcheckingStrategy(element, language);
        if(strategy == null)
          return;

        if(!strategy.elementFitsScope(element, scope))
          return;

        PsiFile containingFile = holder.getFile();
        if (Boolean.TRUE.equals(containingFile.getUserData(InjectedLanguageManager.FRANKENSTEIN_INJECTION))) {
          return;
        }

        tokenize(element, language, new MyTokenConsumer(manager, holder, LanguageNamesValidation.INSTANCE.forLanguage(language)), scope);
      }
    };
  }

  private Set<SpellCheckingScope> buildAllowedScopes() {
    var result = new HashSet<SpellCheckingScope>();
    if(processLiterals)
      result.add(SpellCheckingScope.Literals);
    if(processComments)
      result.add(SpellCheckingScope.Comments);
    if(processCode)
      result.add(SpellCheckingScope.Code);
    return result;
  }

  /**
   * Splits element text in tokens according to spell checker strategy of given language
   *
   * @param element  Psi element
   * @param language Usually element.getLanguage()
   * @param consumer the consumer of tokens
   */
  public static void tokenize(@NotNull final PsiElement element,
                              @NotNull final Language language,
                              TokenConsumer consumer, Set<SpellCheckingScope> allowedScopes) {
    SpellcheckingStrategy factoryByLanguage = getSpellcheckingStrategy(element, language);
    if (factoryByLanguage == null) {
      return;
    }
    tokenize(factoryByLanguage, element, consumer, allowedScopes);
  }

  private static void tokenize(SpellcheckingStrategy strategy, PsiElement element, TokenConsumer consumer, Set<SpellCheckingScope> allowedScopes) {
    var tokenizer = strategy.getTokenizer(element, allowedScopes);
    //noinspection unchecked
    tokenizer.tokenize(element, consumer);
  }

  private static void addBatchDescriptor(@NotNull PsiElement element,
                                         @NotNull TextRange textRange,
                                         @NotNull String word,
                                         @NotNull ProblemsHolder holder) {
    var fixes = SpellcheckingStrategy.getDefaultBatchFixes(element, textRange, word);
    ProblemDescriptor problemDescriptor = createProblemDescriptor(element, textRange, fixes, false);
    holder.registerProblem(problemDescriptor);
  }

  private static void addRegularDescriptor(@NotNull PsiElement element, @NotNull TextRange textRange, @NotNull ProblemsHolder holder,
                                           boolean useRename, String wordWithTypo) {
    SpellcheckingStrategy strategy = getSpellcheckingStrategy(element, element.getLanguage());

    LocalQuickFix[] fixes = strategy != null
                                   ? strategy.getRegularFixes(element, textRange, useRename, wordWithTypo)
                                   : SpellcheckingStrategy.getDefaultRegularFixes(useRename, wordWithTypo, element, textRange);

    final ProblemDescriptor problemDescriptor = createProblemDescriptor(element, textRange, fixes, true);
    holder.registerProblem(problemDescriptor);
  }

  private static ProblemDescriptor createProblemDescriptor(PsiElement element, TextRange textRange,
                                                           LocalQuickFix[] fixes,
                                                           boolean onTheFly) {
    final String description = SpellCheckerBundle.message("typo.in.word.ref");
    return new ProblemDescriptorBase(element, element, description, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                     false, textRange, onTheFly, onTheFly);
  }

  @SuppressWarnings("PublicField")
  public boolean processCode = true;
  public boolean processLiterals = true;
  public boolean processComments = true;

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("processCode", SpellCheckerBundle.message("process.code")),
      checkbox("processLiterals", SpellCheckerBundle.message("process.literals")),
      checkbox("processComments", SpellCheckerBundle.message("process.comments"))
    );
  }

  private static final class MyTokenConsumer extends TokenConsumer implements Consumer<TextRange> {
    private final Set<String> myAlreadyChecked = CollectionFactory.createSmallMemoryFootprintSet();
    private final SpellCheckerManager myManager;
    private final ProblemsHolder myHolder;
    private final NamesValidator myNamesValidator;
    private PsiElement myElement;
    private String myText;
    private boolean myUseRename;
    private int myOffset;

    MyTokenConsumer(SpellCheckerManager manager, ProblemsHolder holder, NamesValidator namesValidator) {
      myManager = manager;
      myHolder = holder;
      myNamesValidator = namesValidator;
    }

    @Override
    public void consumeToken(final PsiElement element,
                             final String text,
                             final boolean useRename,
                             final int offset,
                             TextRange rangeToCheck,
                             Splitter splitter) {
      myElement = element;
      myText = text;
      myUseRename = useRename;
      myOffset = offset;
      splitter.split(text, rangeToCheck, this);
    }

    @Override
    public void consume(TextRange range) {
      // Tokenization of large texts can produce a lot of tokens, but we are inside RA
      ProgressManager.checkCanceled();
      String word = range.substring(myText);
      if (!myHolder.isOnTheFly() && myAlreadyChecked.contains(word)) {
        return;
      }

      boolean keyword = myNamesValidator.isKeyword(word, myElement.getProject());
      if (keyword) {
        return;
      }

      if (myManager.hasProblem(word)) {
        //Use tokenizer to generate accurate range in element (e.g. in case of escape sequences in element)
        SpellcheckingStrategy strategy = getSpellcheckingStrategy(myElement, myElement.getLanguage());

        Tokenizer<?> tokenizer = strategy != null ? strategy.getTokenizer(myElement) : null;
        if (tokenizer != null) {
          range = tokenizer.getHighlightingRange(myElement, myOffset, range);
        }
        assert range.getStartOffset() >= 0;

        if (myHolder.isOnTheFly()) {
          addRegularDescriptor(myElement, range, myHolder, myUseRename, word);
        }
        else {
          myAlreadyChecked.add(word);
          addBatchDescriptor(myElement, range, word, myHolder);
        }
      }
    }
  }

  public enum SpellCheckingScope {
    Comments,
    Literals,
    Code,
  }
}
