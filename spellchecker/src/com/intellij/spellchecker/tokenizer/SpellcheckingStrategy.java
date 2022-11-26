// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.tokenizer;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.spellchecker.SpellCheckerManager.DictionaryLevel;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.quickfixes.ChangeTo;
import com.intellij.spellchecker.quickfixes.RenameTo;
import com.intellij.spellchecker.quickfixes.SaveTo;
import com.intellij.spellchecker.quickfixes.SpellCheckerQuickFix;
import com.intellij.spellchecker.settings.SpellCheckerSettings;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * Defines spellchecking support for a custom language.
 * <p>
 * Register via extension point {@code com.intellij.spellchecker.support}
 * and override {@link #getTokenizer(PsiElement)} to skip/handle specific elements.
 */
public class SpellcheckingStrategy {
  protected final Tokenizer<PsiComment> myCommentTokenizer = new CommentTokenizer();

  public static final ExtensionPointName<KeyedLazyInstance<SpellcheckingStrategy>> EP_NAME =
    new ExtensionPointName<>("com.intellij.spellchecker.support");
  public static final Tokenizer EMPTY_TOKENIZER = new Tokenizer() {
    @Override
    public void tokenize(@NotNull PsiElement element, TokenConsumer consumer) {
    }

    @Override
    public String toString() {
      return "EMPTY_TOKENIZER";
    }
  };

  public static final Tokenizer<PsiElement> TEXT_TOKENIZER = new TokenizerBase<>(PlainTextSplitter.getInstance());

  private static final SpellCheckerQuickFix[] BATCH_FIXES =
    new SpellCheckerQuickFix[]{SaveTo.getSaveToLevelFix(DictionaryLevel.APP), SaveTo.getSaveToLevelFix(DictionaryLevel.PROJECT)};

  /**
   * @return {@link #EMPTY_TOKENIZER} to skip spellchecking, {@link #TEXT_TOKENIZER} for full element text or custom Tokenizer implementation.
   */
  @NotNull
  public Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof PsiWhiteSpace) {
      return EMPTY_TOKENIZER;
    }
    if (element instanceof PsiLanguageInjectionHost && InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)element)) {
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

  public LocalQuickFix[] getRegularFixes(PsiElement element,
                                         @NotNull TextRange textRange,
                                         boolean useRename,
                                         String typo) {
    return getDefaultRegularFixes(useRename, typo, element, textRange);
  }

  public static LocalQuickFix[] getDefaultRegularFixes(boolean useRename, String typo, @Nullable PsiElement element,
                                                       @NotNull TextRange range) {
    ArrayList<LocalQuickFix> result = new ArrayList<>();

    if (useRename) {
      result.add(new RenameTo(typo));
    } else if (element != null) {
      result.addAll(new ChangeTo(typo, element, range).getAllAsFixes());
    }

    if (element == null) {
      result.add(new SaveTo(typo));
      return result.toArray(LocalQuickFix.EMPTY_ARRAY);
    }

    final SpellCheckerSettings settings = SpellCheckerSettings.getInstance(element.getProject());
    if (settings.isUseSingleDictionaryToSave()) {
      result.add(new SaveTo(typo, DictionaryLevel.getLevelByName(settings.getDictionaryToSave())));
      return result.toArray(LocalQuickFix.EMPTY_ARRAY);
    }

    result.add(new SaveTo(typo));
    return result.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  public static SpellCheckerQuickFix[] getDefaultBatchFixes() {
    return BATCH_FIXES;
  }

  public boolean isMyContext(@NotNull PsiElement element) {
    return true;
  }
}
