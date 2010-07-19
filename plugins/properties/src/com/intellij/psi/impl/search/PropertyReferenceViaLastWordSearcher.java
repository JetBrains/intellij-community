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
package com.intellij.psi.impl.search;

import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;

import java.util.List;

/**
 * @author ven
 */
public class PropertyReferenceViaLastWordSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  public PropertyReferenceViaLastWordSearcher() {
    super(true);
  }

  // add to the search results occurences in JSPs of the last word in the property name, since this stuff is possible:
  // <bla:bla ref.key="lastWord> (can reference to xxx.lastWord property)

  @Override
  public void processQuery(ReferencesSearch.SearchParameters queryParameters, Processor<PsiReference> consumer) {
    final PsiElement refElement = queryParameters.getElementToSearch();
    if (!(refElement instanceof Property)) return;

    final String name = ((Property)refElement).getName();
    if (name == null) return;
    final List<String> words = StringUtil.getWordsIn(name);
    if (words.isEmpty()) return;
    final String lastWord = words.get(words.size() - 1);

    SearchScope searchScope = queryParameters.getEffectiveSearchScope();
    if (searchScope instanceof GlobalSearchScope) {
      searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)searchScope, StdFileTypes.JSP, StdFileTypes.JSPX);
    }
    queryParameters.getOptimizer().searchWord(lastWord, searchScope, UsageSearchContext.IN_FOREIGN_LANGUAGES, false, refElement);
  }
}
