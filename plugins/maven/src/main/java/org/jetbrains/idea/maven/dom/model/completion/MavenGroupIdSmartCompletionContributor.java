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
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.impl.GenericDomValueReference;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.converters.MavenArtifactCoordinatesGroupIdConverter;

import java.util.Collection;

public class MavenGroupIdSmartCompletionContributor extends CompletionContributor {

  @Override
  public void fillCompletionVariants(final CompletionParameters parameters, final CompletionResultSet result) {

    if (parameters.getCompletionType() != CompletionType.SMART) {
      return;
    }
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final GenericDomValueReference reference = getReference(parameters);
        if (reference == null) {
          return;
        }

        Collection<String> variants =
          ((MavenArtifactCoordinatesGroupIdConverter)reference.getConverter()).getSmartVariants(reference.getConvertContext());

        for (String variant : variants) {
          if (!StringUtil.isEmptyOrSpaces(variant)) {
            result.addElement(LookupElementBuilder.create(variant));

          }
        }
      }
    });
  }

  @Nullable
  private GenericDomValueReference getReference(CompletionParameters parameters) {

    if (!(parameters.getPosition() instanceof XmlToken)) return null;

    PsiReference[] references = getReferences(parameters);
    for (final PsiReference psiReference : references) {
      if (psiReference instanceof GenericDomValueReference) {
        final Converter converter = ((GenericDomValueReference)psiReference).getConverter();
        if (converter instanceof MavenArtifactCoordinatesGroupIdConverter) {
          return (GenericDomValueReference)psiReference;
        }
      }
    }
    return null;
  }

  private static PsiReference[] getReferences(CompletionParameters parameters) {
    PsiElement psiElement = parameters.getPosition().getParent();

    return psiElement instanceof XmlText ? psiElement.getParent().getReferences() : psiElement.getReferences();
  }
}