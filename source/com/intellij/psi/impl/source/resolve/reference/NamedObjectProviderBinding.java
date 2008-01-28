package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiElement;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ReflectionCache;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.patterns.ElementPattern;
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

  private final ConcurrentMap<String, CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementPattern,Double>>> myNamesToProvidersMap = new ConcurrentHashMap<String, CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementPattern,Double>>>(5);
  private final ConcurrentMap<String, CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementPattern,Double>>> myNamesToProvidersMapInsensitive = new ConcurrentHashMap<String, CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementPattern,Double>>>(5);
  private final List<Trinity<PsiReferenceProvider, ElementPattern,Double>> myProvidersWithoutNames = new CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementPattern,Double>>();

  public void registerProvider(@NonNls String[] names, ElementPattern filter, boolean caseSensitive, PsiReferenceProvider provider,
                               final Double priority) {
    if (names == null || names.length == 0) {
      myProvidersWithoutNames.add(new Trinity<PsiReferenceProvider, ElementPattern,Double>(provider, filter, priority));
    }
    else {
      final ConcurrentMap<String, CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementPattern,Double>>> map = caseSensitive ? myNamesToProvidersMap : myNamesToProvidersMapInsensitive;

      for (final String attributeName : names) {
        CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementPattern,Double>> psiReferenceProviders = map.get(attributeName);

        if (psiReferenceProviders == null) {
          psiReferenceProviders = ConcurrencyUtil.cacheOrGet(map, caseSensitive ? attributeName : attributeName.toLowerCase(), new CopyOnWriteArrayList<Trinity<PsiReferenceProvider, ElementPattern,Double>>());
        }

        psiReferenceProviders.add(new Trinity<PsiReferenceProvider, ElementPattern,Double>(provider, filter, priority));
      }
    }
  }

  public void addAcceptableReferenceProviders(@NotNull PsiElement position, @NotNull List<Trinity<PsiReferenceProvider, ElementPattern, Double>> list) {
    if (!ReflectionCache.isInstance(position, myClass)) return;

    String name = getName(position);
    if (name != null) {
      List<Trinity<PsiReferenceProvider, ElementPattern,Double>> psiReferenceProviders = myNamesToProvidersMap.get(name);

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
                                           final List<Trinity<PsiReferenceProvider,ElementPattern,Double>> providerList,
                                           final List<Trinity<PsiReferenceProvider,ElementPattern,Double>> ret) {
    for(final Trinity<PsiReferenceProvider,ElementPattern,Double> pair:providerList) {
      final ElementPattern ElementPattern = pair.getSecond();
      if (ElementPattern == null || ElementPattern.accepts(position)) {
        ret.add(pair);
      }
    }
  }
}
