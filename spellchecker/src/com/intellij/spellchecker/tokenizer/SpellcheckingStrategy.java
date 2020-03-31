// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.tokenizer;

import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.spellchecker.SpellCheckerManager.DictionaryLevel;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.quickfixes.ChangeTo;
import com.intellij.spellchecker.quickfixes.RenameTo;
import com.intellij.spellchecker.quickfixes.SaveTo;
import com.intellij.spellchecker.quickfixes.SpellCheckerQuickFix;
import com.intellij.spellchecker.settings.SpellCheckerSettings;
import com.intellij.util.KeyedLazyInstance;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines spellchecking support for a custom language.
 * <p>
 * Register via extension point {@code com.intellij.spellchecker.support}
 * and override {@link #getTokenizer(PsiElement)} to skip/handle specific elements.
 */
public class SpellcheckingStrategy {
  protected final Tokenizer<PsiComment> myCommentTokenizer = new CommentTokenizer();
  protected final Tokenizer<XmlAttributeValue> myXmlAttributeTokenizer = new XmlAttributeValueTokenizer();

  public static final ExtensionPointName<KeyedLazyInstance<SpellcheckingStrategy>> EP_NAME = ExtensionPointName.create("com.intellij.spellchecker.support");
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
      return myCommentTokenizer;
    }
    if (element instanceof XmlAttributeValue) return myXmlAttributeTokenizer;
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

  public SpellCheckerQuickFix[] getRegularFixes(PsiElement element,
                                                int offset,
                                                @NotNull TextRange textRange,
                                                boolean useRename,
                                                String wordWithTypo) {
    return getDefaultRegularFixes(useRename, wordWithTypo, element);
  }

  /**
   * @deprecated will be removed in 2018.X, use @link {@link SpellcheckingStrategy#getDefaultRegularFixes(boolean, String, PsiElement)} instead
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2018.3")
  @Deprecated
  public static SpellCheckerQuickFix[] getDefaultRegularFixes(boolean useRename, String wordWithTypo) {
    return getDefaultRegularFixes(useRename, wordWithTypo, null);
  }

  public static SpellCheckerQuickFix[] getDefaultRegularFixes(boolean useRename, String wordWithTypo, @Nullable PsiElement element) {
    final SpellCheckerSettings settings = element != null ? SpellCheckerSettings.getInstance(element.getProject()) : null;
    if (settings != null && settings.isUseSingleDictionaryToSave()) {
      return new SpellCheckerQuickFix[]{useRename ? new RenameTo(wordWithTypo) : new ChangeTo(wordWithTypo),
        new SaveTo(wordWithTypo, DictionaryLevel.getLevelByName(settings.getDictionaryToSave()))};
    }
    return new SpellCheckerQuickFix[]{useRename ? new RenameTo(wordWithTypo) : new ChangeTo(wordWithTypo), new SaveTo(wordWithTypo)};
  }

  public static SpellCheckerQuickFix[] getDefaultBatchFixes() {
    return BATCH_FIXES;
  }

  protected static class XmlAttributeValueTokenizer extends Tokenizer<XmlAttributeValue> {
    @Override
    public void tokenize(@NotNull final XmlAttributeValue element, final TokenConsumer consumer) {
      if (element instanceof PsiLanguageInjectionHost && InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)element)) return;

      final String valueTextTrimmed = element.getValue().trim();
      // do not inspect colors like #00aaFF
      if (valueTextTrimmed.startsWith("#") && valueTextTrimmed.length() <= 7 && isHexString(valueTextTrimmed.substring(1))) {
        return;
      }

      consumer.consumeToken(element, PlainTextSplitter.getInstance());
    }

    private static boolean isHexString(final String s) {
      for (int i = 0; i < s.length(); i++) {
        if (!StringUtil.isHexDigit(s.charAt(i))) {
          return false;
        }
      }
      return true;
    }
  }

  public boolean isMyContext(@NotNull PsiElement element) {
    return true;
  }
}
