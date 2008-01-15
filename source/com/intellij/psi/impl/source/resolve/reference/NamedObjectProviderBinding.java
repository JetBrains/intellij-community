package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ReflectionCache;
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

  private final ConcurrentMap<String, CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementFilter,Double>>> myNamesToProvidersMap = new ConcurrentHashMap<String, CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementFilter,Double>>>(5);
  private final ConcurrentMap<String, CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementFilter,Double>>> myNamesToProvidersMapInsensitive = new ConcurrentHashMap<String, CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementFilter,Double>>>(5);
  private final List<Trinity<PsiReferenceProvider, ElementFilter,Double>> myProvidersWithoutNames = new CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementFilter,Double>>();

  public void registerProvider(@NonNls String[] names, ElementFilter filter, boolean caseSensitive, PsiReferenceProvider provider,
                               final Double priority) {
    if (names == null || names.length == 0) {
      myProvidersWithoutNames.add(new Trinity<PsiReferenceProvider, ElementFilter,Double>(provider, filter, priority));
    }
    else {
      final ConcurrentMap<String, CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementFilter,Double>>> map = caseSensitive ? myNamesToProvidersMap : myNamesToProvidersMapInsensitive;

      for (final String attributeName : names) {
        CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementFilter,Double>> psiReferenceProviders = map.get(attributeName);

        if (psiReferenceProviders == null) {
          psiReferenceProviders = ConcurrencyUtil.cacheOrGet(map, caseSensitive ? attributeName : attributeName.toLowerCase(), new CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementFilter,Double>>());
        }

        psiReferenceProviders.add(new Trinity<PsiReferenceProvider, ElementFilter,Double>(provider, filter, priority));
      }
    }
  }

  public void addAcceptableReferenceProviders(@NotNull PsiElement position, @NotNull List<Trinity<PsiReferenceProvider, ElementFilter, Double>> list) {
    if (!ReflectionCache.isInstance(position, myClass)) return;

    String name = getName(position);
    if (name != null) {
      List<Trinity<PsiReferenceProvider, ElementFilter,Double>> psiReferenceProviders = myNamesToProvidersMap.get(name);

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

  private static void addMatchingProviders(final PsiElement position,
                                           final List<Trinity<PsiReferenceProvider,ElementFilter,Double>> providerList,
                                           final List<Trinity<PsiReferenceProvider,ElementFilter,Double>> ret) {
    for(final Trinity<PsiReferenceProvider,ElementFilter,Double> pair:providerList) {
      final ElementFilter elementFilter = pair.getSecond();
      if (elementFilter == null || elementFilter.isAcceptable(position,position)) {
        ret.add(pair);
      }
    }
  }
}
