// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.html;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.LineMarkerProviders;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author Maxim.Mossienko
 */
public final class HtmlLineMarkerProvider implements LineMarkerProvider {
  @Override
  public LineMarkerInfo<?> getLineMarkerInfo(final @NotNull PsiElement element) {
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
  public void collectSlowLineMarkers(final @NotNull List<? extends PsiElement> elements, final @NotNull Collection<? super LineMarkerInfo<?>> result) {
    Map<LineMarkerProvider, List<PsiElement>> embeddedLineMarkersWorkItems = null;

    for(PsiElement element:elements) {
      if(element instanceof PsiWhiteSpace) continue;
      final Language language = element.getLanguage();

      if (!(language instanceof XMLLanguage)) {
        List<LineMarkerProvider> lineMarkerProviders = LineMarkerProviders.getInstance().allForLanguage(language);
        for (LineMarkerProvider provider : lineMarkerProviders) {
          if (provider instanceof HtmlLineMarkerProvider) continue;
          if (embeddedLineMarkersWorkItems == null) {
            embeddedLineMarkersWorkItems = new HashMap<>();
          }
          embeddedLineMarkersWorkItems.computeIfAbsent(provider, k -> new ArrayList<>(5)).add(element);
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
