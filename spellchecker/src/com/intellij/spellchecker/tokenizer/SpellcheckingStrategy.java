/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.spellchecker.tokenizer;

import com.intellij.codeInspection.SuppressionUtil;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlToken;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.inspections.TextSplitter;
import com.intellij.spellchecker.quickfixes.AcceptWordAsCorrect;
import com.intellij.spellchecker.quickfixes.ChangeTo;
import com.intellij.spellchecker.quickfixes.RenameTo;
import com.intellij.spellchecker.quickfixes.SpellCheckerQuickFix;
import org.jetbrains.annotations.NotNull;

public class SpellcheckingStrategy {
  protected final Tokenizer<PsiComment> myCommentTokenizer = new CommentTokenizer();
  protected final Tokenizer<XmlAttributeValue> myXmlAttributeTokenizer = new XmlAttributeValueTokenizer();

  public static final ExtensionPointName<SpellcheckingStrategy> EP_NAME = ExtensionPointName.create("com.intellij.spellchecker.support");
  public static final Tokenizer EMPTY_TOKENIZER = new Tokenizer() {
    @Override
    public void tokenize(@NotNull PsiElement element, TokenConsumer consumer) {
    }
  };

  public static final Tokenizer<PsiElement> TEXT_TOKENIZER = new TokenizerBase<>(PlainTextSplitter.getInstance());

  private static final SpellCheckerQuickFix[] BATCH_FIXES = new SpellCheckerQuickFix[]{new AcceptWordAsCorrect()};

  @NotNull
  public Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof PsiWhiteSpace) {
      return EMPTY_TOKENIZER;
    }
    if (element instanceof PsiLanguageInjectionHost && InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)element)) {
      return EMPTY_TOKENIZER;
    }
    if (element instanceof PsiNameIdentifierOwner) return new PsiIdentifierOwnerTokenizer();
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
    if (element instanceof XmlToken) {
      if (((XmlToken)element).getTokenType() == XmlTokenType.XML_DATA_CHARACTERS) {
        PsiElement injection = InjectedLanguageManager.getInstance(element.getProject()).findInjectedElementAt(element.getContainingFile(), element.getTextOffset());
        if (injection == null) {
          return TEXT_TOKENIZER;
        }
      }

    }
    return EMPTY_TOKENIZER;
  }

  public SpellCheckerQuickFix[] getRegularFixes(PsiElement element,
                                                int offset,
                                                @NotNull TextRange textRange,
                                                boolean useRename,
                                                String wordWithTypo) {
    return getDefaultRegularFixes(useRename, wordWithTypo);
  }

  public static SpellCheckerQuickFix[] getDefaultRegularFixes(boolean useRename, String wordWithTypo) {
    return new SpellCheckerQuickFix[]{
      useRename ? new RenameTo(wordWithTypo) : new ChangeTo(wordWithTypo),
      new AcceptWordAsCorrect(wordWithTypo)
    };
  }

  public static SpellCheckerQuickFix[] getDefaultBatchFixes() {
    return BATCH_FIXES;
  }

  protected static class XmlAttributeValueTokenizer extends Tokenizer<XmlAttributeValue> {
    public void tokenize(@NotNull final XmlAttributeValue element, final TokenConsumer consumer) {
      if (element instanceof PsiLanguageInjectionHost && InjectedLanguageUtil.hasInjections((PsiLanguageInjectionHost)element)) return;

      final String valueTextTrimmed = element.getValue().trim();
      // do not inspect colors like #00aaFF
      if (valueTextTrimmed.startsWith("#") && valueTextTrimmed.length() <= 7 && isHexString(valueTextTrimmed.substring(1))) {
        return;
      }

      consumer.consumeToken(element, TextSplitter.getInstance());
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
