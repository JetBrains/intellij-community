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
package com.intellij.html;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.LineMarkerProviders;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Maxim.Mossienko
 */
public class HtmlLineMarkerProvider implements LineMarkerProvider {
  private final Map<Language, LineMarkerProvider> embeddedLanguagesLineMarkerProviders = new THashMap<>();

  @Override
  public LineMarkerInfo getLineMarkerInfo(@NotNull final PsiElement element) {
    if (element instanceof PsiWhiteSpace) return null;
    final Language language = element.getLanguage();

    if (!(language instanceof XMLLanguage)) {
      final LineMarkerProvider markerProvider = getLineMarkerProviderFromLanguage(language, embeddedLanguagesLineMarkerProviders);

      if (markerProvider != null) return markerProvider.getLineMarkerInfo(element);
    }
    return null;
  }

  private static LineMarkerProvider getLineMarkerProviderFromLanguage(final Language language,
                                                               final Map<Language, LineMarkerProvider> embeddedLanguagesLineMarkerProviders) {
    final LineMarkerProvider markerProvider;

    if (!embeddedLanguagesLineMarkerProviders.containsKey(language)) {
      embeddedLanguagesLineMarkerProviders.put(language, markerProvider = LineMarkerProviders.INSTANCE.forLanguage(language));
    }
    else {
      markerProvider = embeddedLanguagesLineMarkerProviders.get(language);
    }
    return markerProvider;
  }

  @Override
  public void collectSlowLineMarkers(@NotNull final List<PsiElement> elements, @NotNull final Collection<LineMarkerInfo> result) {
    Map<Language, LineMarkerProvider> localEmbeddedLanguagesLineMarkerProviders = null;
    Map<LineMarkerProvider, List<PsiElement>> embeddedLineMarkersWorkItems = null;

    for(PsiElement element:elements) {
      if(element instanceof PsiWhiteSpace) continue;
      final Language language = element.getLanguage();

      if (!(language instanceof XMLLanguage)) {
        if(localEmbeddedLanguagesLineMarkerProviders == null) {
          localEmbeddedLanguagesLineMarkerProviders = new THashMap<>();
        }

        final LineMarkerProvider lineMarkerProvider = getLineMarkerProviderFromLanguage(language, localEmbeddedLanguagesLineMarkerProviders);

        if (lineMarkerProvider != null) {
          if (embeddedLineMarkersWorkItems == null) embeddedLineMarkersWorkItems = new THashMap<>();
          List<PsiElement> elementList = embeddedLineMarkersWorkItems.computeIfAbsent(lineMarkerProvider, k -> new ArrayList<>(5));

          elementList.add(element);
        }
      }
    }

    if (embeddedLineMarkersWorkItems != null) {
      for(Map.Entry<LineMarkerProvider, List<PsiElement>> entry:embeddedLineMarkersWorkItems.entrySet()) {
        entry.getKey().collectSlowLineMarkers(entry.getValue(), result);
      }
    }
  }
}
