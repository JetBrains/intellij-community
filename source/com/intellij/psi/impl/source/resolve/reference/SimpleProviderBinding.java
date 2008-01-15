package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.util.ReflectionCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 01.04.2003
 * Time: 16:52:28
 * To change this template use Options | File Templates.
 */
public class SimpleProviderBinding implements ProviderBinding {
  private final Class myScope;
  private final List<Trinity<PsiReferenceProvider,ElementFilter,Double>> myProviderPairs = new CopyOnWriteArrayList<Trinity<PsiReferenceProvider,ElementFilter,Double>>();

  public SimpleProviderBinding(Class scope){
    myScope = scope;
  }

  private boolean isAcceptable(PsiElement position, ElementFilter filter){
    if(position == null) return false;

    if (ReflectionCache.isAssignable(myScope, position.getClass())) {
      return filter == null || filter.isAcceptable(position, position);
    }

    return false;
  }

  public void registerProvider(PsiReferenceProvider provider,ElementFilter elementFilter,@Nullable Double priority){
    myProviderPairs.add(Trinity.create(provider, elementFilter,priority == null ? ReferenceProvidersRegistry.DEFAULT_PRIORITY : priority));
  }

  public void addAcceptableReferenceProviders(@NotNull PsiElement position, @NotNull List<Trinity<PsiReferenceProvider, ElementFilter, Double>> list) {
    for(Trinity<PsiReferenceProvider,ElementFilter,Double> pair:myProviderPairs) {
      if (isAcceptable(position,pair.second)) {
        list.add(pair);
      }
    }
  }

  public void unregisterProvider(PsiReferenceProvider provider, ElementFilter elementFilter) {
    myProviderPairs.remove(Trinity.create(provider, elementFilter, ReferenceProvidersRegistry.DEFAULT_PRIORITY));
  }
}
