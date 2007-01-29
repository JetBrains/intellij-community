package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author maxim
 */
abstract class NamedObjectProviderBinding implements ProviderBinding {
  private final @NotNull Class myClass;
  
  NamedObjectProviderBinding(@NotNull Class _class) {
    myClass = _class;
  }

  private final ConcurrentMap<String, CopyOnWriteArrayList<Pair<PsiReferenceProvider, ElementFilter>>> myNamesToProvidersMap = new ConcurrentHashMap<String, CopyOnWriteArrayList<Pair<PsiReferenceProvider, ElementFilter>>>(5);
  private final ConcurrentMap<String, CopyOnWriteArrayList<Pair<PsiReferenceProvider, ElementFilter>>> myNamesToProvidersMapInsensitive = new ConcurrentHashMap<String, CopyOnWriteArrayList<Pair<PsiReferenceProvider, ElementFilter>>>(5);
  private final List<Pair<PsiReferenceProvider, ElementFilter>> myProvidersWithoutNames = new CopyOnWriteArrayList<Pair<PsiReferenceProvider, ElementFilter>>();

  public void registerProvider(@NonNls String[] names, ElementFilter filter, boolean caseSensitive, PsiReferenceProvider provider) {
    if (names == null || names.length == 0) {
      myProvidersWithoutNames.add(new Pair<PsiReferenceProvider, ElementFilter>(provider, filter));
    }
    else {
      final ConcurrentMap<String, CopyOnWriteArrayList<Pair<PsiReferenceProvider, ElementFilter>>> map = caseSensitive ? myNamesToProvidersMap : myNamesToProvidersMapInsensitive;

      for (final String attributeName : names) {
        CopyOnWriteArrayList<Pair<PsiReferenceProvider, ElementFilter>> psiReferenceProviders = map.get(attributeName);

        if (psiReferenceProviders == null) {
          psiReferenceProviders = ConcurrencyUtil.cacheOrGet(map, caseSensitive ? attributeName : attributeName.toLowerCase(), new CopyOnWriteArrayList<Pair<PsiReferenceProvider, ElementFilter>>());
        }

        psiReferenceProviders.add(new Pair<PsiReferenceProvider, ElementFilter>(provider, filter));
      }
    }
  }

  public void addAcceptableReferenceProviders(@NotNull PsiElement position, @NotNull List<PsiReferenceProvider> list) {
    if (!myClass.isInstance(position)) return;

    String name = getName(position);
    if (name != null) {
      List<Pair<PsiReferenceProvider, ElementFilter>> psiReferenceProviders = myNamesToProvidersMap.get(name);

      if (psiReferenceProviders != null) {
        addMatchingProviders(position, psiReferenceProviders, list);
      }

      psiReferenceProviders = myNamesToProvidersMapInsensitive.get(name.toLowerCase());

      if (psiReferenceProviders != null) {
        addMatchingProviders(position, psiReferenceProviders, list);
      }
    }

    addMatchingProviders(position, myProvidersWithoutNames, list);
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
