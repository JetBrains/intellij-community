/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.spellchecker.xml;

import com.intellij.codeInsight.completion.HtmlCompletionContributor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.impl.source.html.HtmlDocumentImpl;
import com.intellij.psi.xml.*;
import com.intellij.spellchecker.inspections.PlainTextSplitter;
import com.intellij.spellchecker.inspections.Splitter;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import com.intellij.util.SmartList;
import com.intellij.util.io.URLUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class HtmlSpellcheckingStrategy extends XmlSpellcheckingStrategy {

  private final Tokenizer<? extends PsiElement> myDocumentTextTokenizer = createDocumentTextTokenizer();

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof HtmlDocumentImpl) {
      return myDocumentTextTokenizer;
    }
    if (element instanceof XmlAttributeValue) {
      if (URLUtil.isDataUri(ElementManipulators.getValueText(element))) {
        return EMPTY_TOKENIZER;
      }
      PsiElement parent = element.getParent();
      if (parent instanceof XmlAttribute) {
        if (HtmlCompletionContributor.hasHtmlAttributesCompletion(element) &&
            HtmlCompletionContributor.addSpecificCompletions((XmlAttribute)parent).length > 0) {
          return EMPTY_TOKENIZER;
        }
        XmlAttributeDescriptor descriptor = ((XmlAttribute)parent).getDescriptor();
        if (descriptor != null && (descriptor.isEnumerated() || descriptor.isFixed())) {
          return EMPTY_TOKENIZER;
        }
      }
    }
    return super.getTokenizer(element);
  }

  @Override
  protected boolean isXmlDataCharactersParentHandled(@NotNull PsiElement parent) {
    return super.isXmlDataCharactersParentHandled(parent)
           || parent instanceof HtmlDocumentImpl;
  }

  protected Tokenizer<? extends PsiElement> createDocumentTextTokenizer() {
    return new HtmlDocumentTextTokenizer(PlainTextSplitter.getInstance());
  }

  protected static class HtmlDocumentTextTokenizer extends XmlTokenizerBase<HtmlDocumentImpl> {

    public HtmlDocumentTextTokenizer(Splitter splitter) {
      super(splitter);
    }

    @Override
    protected @NotNull List<@NotNull SpellcheckRange> getSpellcheckRanges(@NotNull HtmlDocumentImpl element) {
      var result = new SmartList<SpellcheckRange>();
      element.acceptChildren(new XmlElementVisitor() {
        @Override
        public void visitXmlToken(@NotNull XmlToken token) {
          if (token.getTokenType() == XmlTokenType.XML_DATA_CHARACTERS) {
            var text = token.getText();
            result.add(new SpellcheckRange(text, false, token.getStartOffsetInParent(), TextRange.allOf(text)));
          }
        }
      });
      return result;
    }

    @Override
    protected @NotNull List<@NotNull TextRange> getSpellcheckOuterContentRanges(@NotNull HtmlDocumentImpl element) {
      List<TextRange> result = new SmartList<>(super.getSpellcheckOuterContentRanges(element));
      element.acceptChildren(new XmlElementVisitor() {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          if (!isContentElement(element)) {
            result.add(element.getTextRangeInParent());
          }
        }
      });
      return result;
    }

    protected boolean isContentElement(@NotNull PsiElement element) {
      var tokenType = element.getNode().getElementType();
      if (tokenType == XmlTokenType.XML_DATA_CHARACTERS
          || XmlTokenType.WHITESPACES.contains(tokenType)) {
        return true;
      }
      if (tokenType == XmlTokenType.XML_ENTITY_REF_TOKEN
          || tokenType == XmlTokenType.XML_CHAR_ENTITY_REF) {
        return false;
      }
      return element instanceof XmlTag
             || element instanceof XmlComment
             || element instanceof XmlProlog
             || element instanceof XmlProcessingInstruction;
    }
  }
}