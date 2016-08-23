/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.idea.maven.dom.model.completion;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.SmartList;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.xml.impl.GenericDomValueReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.converters.MavenSmartConverter;
import org.jetbrains.idea.maven.dom.references.MavenPropertyCompletionContributor;

import java.util.Collection;
import java.util.Collections;

public class MavenSmartCompletionContributor extends CompletionContributor {
  @Override
  public void fillCompletionVariants(@NotNull final CompletionParameters parameters, @NotNull CompletionResultSet result) {
    if (parameters.getCompletionType() != CompletionType.SMART) return;

    Collection<?> variants = getVariants(parameters);

    MavenPropertyCompletionContributor.addVariants(variants, result);
  }

  @NotNull
  private static Collection<?> getVariants(CompletionParameters parameters) {
    if (!MavenDomUtil.isMavenFile(parameters.getOriginalFile())) return Collections.emptyList();

    SmartList<?> result = new SmartList<>();

    for (PsiReference each : getReferences(parameters)) {
      if (each instanceof TagNameReference) continue;

      if (each instanceof GenericDomValueReference) {
        GenericDomValueReference reference = (GenericDomValueReference)each;

        Converter converter = reference.getConverter();

        if (converter instanceof MavenSmartConverter) {
          Collection variants = ((MavenSmartConverter)converter).getSmartVariants(reference.getConvertContext());
          if (converter instanceof ResolvingConverter) {
            addVariants((ResolvingConverter)converter, variants, result);
          }
          else {
            result.addAll(variants);
          }
        }
        else if (converter instanceof ResolvingConverter) {
          //noinspection unchecked
          ResolvingConverter resolvingConverter = (ResolvingConverter)converter;
          Collection variants = resolvingConverter.getVariants(reference.getConvertContext());
          addVariants(resolvingConverter, variants, result);
        }
      }
      else {
        //noinspection unchecked
        Collections.addAll((Collection)result, each.getVariants());
      }
    }
    return result;
  }

  private static <T> void addVariants(ResolvingConverter<T> converter, Collection<T> variants, Collection result) {
    for (T variant : variants) {
      LookupElement lookupElement = converter.createLookupElement(variant);
      if (lookupElement != null) {
        result.add(lookupElement);
      }
      else {
        result.add(variant);
      }
    }
  }

  @NotNull
  private static PsiReference[] getReferences(CompletionParameters parameters) {
    PsiElement psiElement = parameters.getPosition().getParent();
    return psiElement instanceof XmlText ? psiElement.getParent().getReferences() : psiElement.getReferences();
  }
}