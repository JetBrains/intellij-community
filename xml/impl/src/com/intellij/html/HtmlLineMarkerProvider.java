package com.intellij.html;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.codeInsight.daemon.LineMarkerProviders;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import gnu.trove.THashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Maxim.Mossienko
 *         Date: Oct 14, 2008
 *         Time: 11:38:46 PM
 */
public class HtmlLineMarkerProvider implements LineMarkerProvider {
  private final Map<Language, LineMarkerProvider> embeddedLanguagesLineMarkerProviders = new THashMap<Language, LineMarkerProvider>();

  public LineMarkerInfo getLineMarkerInfo(final PsiElement element) {
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
    } else {
      markerProvider = embeddedLanguagesLineMarkerProviders.get(language);
    }
    return markerProvider;
  }

  public void collectSlowLineMarkers(final List<PsiElement> elements, final Collection<LineMarkerInfo> result) {
    Map<Language, LineMarkerProvider> localEmbeddedLanguagesLineMarkerProviders = null;
    Map<LineMarkerProvider, List<PsiElement>> embeddedLineMarkersWorkItems = null;

    for(PsiElement element:elements) {
      if(element instanceof PsiWhiteSpace) continue;
      final Language language = element.getLanguage();

      if (!(language instanceof XMLLanguage)) {
        if(localEmbeddedLanguagesLineMarkerProviders == null) {
          localEmbeddedLanguagesLineMarkerProviders = new THashMap<Language, LineMarkerProvider>();
        }

        final LineMarkerProvider lineMarkerProvider = getLineMarkerProviderFromLanguage(language, localEmbeddedLanguagesLineMarkerProviders);

        if (lineMarkerProvider != null) {
          if (embeddedLineMarkersWorkItems == null) embeddedLineMarkersWorkItems = new THashMap<LineMarkerProvider, List<PsiElement>>();
          List<PsiElement> elementList = embeddedLineMarkersWorkItems.get(lineMarkerProvider);

          if (elementList == null) {
            elementList = new ArrayList<PsiElement>(5);
            embeddedLineMarkersWorkItems.put(lineMarkerProvider, elementList);
          }

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
