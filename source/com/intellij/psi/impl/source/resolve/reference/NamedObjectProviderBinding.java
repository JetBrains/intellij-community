package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author maxim
 */
abstract class NamedObjectProviderBinding implements ProviderBinding {
  private final @NotNull Class myClass;
  
  NamedObjectProviderBinding(@NotNull Class _class) {
    myClass = _class;
  }
  
  private Map<String, List<Pair<PsiReferenceProvider,ElementFilter>>> myNamesToProvidersMap =
    new HashMap<String,List<Pair<PsiReferenceProvider,ElementFilter>>>(5);
  private Map<String, List<Pair<PsiReferenceProvider,ElementFilter>>> myNamesToProvidersMapInsensitive =
    new HashMap<String,List<Pair<PsiReferenceProvider,ElementFilter>>>(5);

  private List<Pair<PsiReferenceProvider,ElementFilter>> myProvidersWithoutNames =
    new ArrayList<Pair<PsiReferenceProvider,ElementFilter>>(5);

  public void registerProvider(@NonNls String[] names, ElementFilter filter,boolean caseSensitive,PsiReferenceProvider provider) {
    if (names == null || names.length == 0) {
      myProvidersWithoutNames.add(new Pair<PsiReferenceProvider,ElementFilter>(provider,filter));
    } else {
      final Map<String, List<Pair<PsiReferenceProvider, ElementFilter>>> map =
        caseSensitive ? myNamesToProvidersMap : myNamesToProvidersMapInsensitive;

      for(final String attributeName:names) {
        List<Pair<PsiReferenceProvider,ElementFilter>> psiReferenceProviders =
          map.get(attributeName);

        if (psiReferenceProviders == null) {
          psiReferenceProviders = new ArrayList<Pair<PsiReferenceProvider,ElementFilter>>(1);
          map.put(caseSensitive ? attributeName:attributeName.toLowerCase(),psiReferenceProviders);
        }

        psiReferenceProviders.add(new Pair<PsiReferenceProvider,ElementFilter>(provider,filter));
      }
    }
  }

  public void addAcceptableReferenceProviders(@NotNull PsiElement position, @NotNull List<PsiReferenceProvider> list) {
    if (!myClass.isInstance(position)) return;

    int initialListSize = list.size();

    String name = getName(position);
    if (name != null) {
      List<Pair<PsiReferenceProvider,ElementFilter>> psiReferenceProviders = myNamesToProvidersMap.get(name);

      if (psiReferenceProviders != null) {
        addMatchingProviders(position, psiReferenceProviders, list);
      }

      psiReferenceProviders = myNamesToProvidersMapInsensitive.get(name.toLowerCase());

      if (psiReferenceProviders != null) {
        addMatchingProviders(position, psiReferenceProviders, list);
      }
    }

    if (list.size() == initialListSize) {
      // no specific provider found; trying to find "common" providers...
      addMatchingProviders(position, myProvidersWithoutNames,list);
    }

  }

  abstract protected String getName(final PsiElement position);

  private static void addMatchingProviders(final PsiElement position, final List<Pair<PsiReferenceProvider,ElementFilter>> providerList, final List<PsiReferenceProvider> ret) {
    for(final Pair<PsiReferenceProvider,ElementFilter> pair:providerList) {
      final ElementFilter elementFilter = pair.getSecond();
      if (elementFilter == null || elementFilter.isAcceptable(position,position)) {
        ret.add(pair.getFirst());
      }
    }
  }
}
