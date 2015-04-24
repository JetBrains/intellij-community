/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.javaee.ExternalResourceManagerEx;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.DependentNSReference;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Dmitry Avdeev
 */
public class XmlLocationCompletionContributor extends CompletionContributor {

  @Override
  public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    PsiReference reference = parameters.getPosition().getContainingFile().findReferenceAt(parameters.getOffset());
    if (reference instanceof PsiMultiReference) reference = ((PsiMultiReference)reference).getReferences()[0];
    if (reference instanceof DependentNSReference) {
      MultiMap<String, String> map = ExternalResourceManagerEx.getInstanceEx().getUrlsByNamespace(parameters.getOriginalFile().getProject());
      String namespace = ((DependentNSReference)reference).getNamespaceReference().getCanonicalText();
      Collection<String> strings = map.get(namespace);
      for (String string : strings) {
        if (!namespace.equals(string)) { // exclude namespaces from location urls
          result.consume(PrioritizedLookupElement.withPriority(LookupElementBuilder.create(string), 100));
        }
      }
      if (!strings.isEmpty()) result.stopHere();
    }
  }
}
