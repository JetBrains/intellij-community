// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @Override
  public LineMarkerInfo<?> getLineMarkerInfo(@NotNull final PsiElement element) {
    if (element instanceof PsiWhiteSpace) return null;
    final Language language = element.getLanguage();

    if (!(language instanceof XMLLanguage)) {
      List<LineMarkerProvider> markerProviders = LineMarkerProviders.getInstance().allForLanguage(language);
      for (LineMarkerProvider provider : markerProviders) {
        if (provider instanceof HtmlLineMarkerProvider) continue;
        LineMarkerInfo<?> info = provider.getLineMarkerInfo(element);
        if (info != null) {
          return info;
        }
      }
    }
    return null;
  }

  @Override
  public void collectSlowLineMarkers(@NotNull final List<PsiElement> elements, @NotNull final Collection<LineMarkerInfo> result) {
    Map<LineMarkerProvider, List<PsiElement>> embeddedLineMarkersWorkItems = null;

    for(PsiElement element:elements) {
      if(element instanceof PsiWhiteSpace) continue;
      final Language language = element.getLanguage();

      if (!(language instanceof XMLLanguage)) {
        List<LineMarkerProvider> lineMarkerProviders = LineMarkerProviders.getInstance().allForLanguage(language);
        for (LineMarkerProvider provider : lineMarkerProviders) {
          if (provider instanceof HtmlLineMarkerProvider) continue;
          if (embeddedLineMarkersWorkItems == null) embeddedLineMarkersWorkItems = new THashMap<>();
          List<PsiElement> elementList = embeddedLineMarkersWorkItems.computeIfAbsent(provider, k -> new ArrayList<>(5));

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
