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
package com.intellij.spellchecker.tokenizer;

import com.intellij.codeInsight.completion.HtmlCompletionContributor;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.io.URLUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import org.jetbrains.annotations.NotNull;

public class HtmlSpellcheckingStrategy extends SpellcheckingStrategy {
  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof PsiComment) return myCommentTokenizer;
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

      return myXmlAttributeTokenizer;
    }
    return EMPTY_TOKENIZER;
  }
}