package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.openapi.util.Trinity;
import com.intellij.psi.PsiElement;
import com.intellij.util.ReflectionCache;
import com.intellij.patterns.ElementPattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;
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
  private final List<Trinity<PsiReferenceProvider,ElementPattern,Double>> myProviderPairs = new CopyOnWriteArrayList<Trinity<PsiReferenceProvider,ElementPattern,Double>>();

  public SimpleProviderBinding(Class scope){
    myScope = scope;
  }

  private boolean isAcceptable(PsiElement position, ElementPattern filter){
    if(position == null) return false;

    if (ReflectionCache.isAssignable(myScope, position.getClass())) {
      return filter == null || filter.accepts(position);
    }

    return false;
  }

  public void registerProvider(PsiReferenceProvider provider,ElementPattern pattern,@Nullable Double priority){
    myProviderPairs.add(Trinity.create(provider, pattern, priority == null ? ReferenceProvidersRegistry.DEFAULT_PRIORITY : priority));
  }

  public void addAcceptableReferenceProviders(@NotNull PsiElement position, @NotNull List<Trinity<PsiReferenceProvider, ElementPattern, Double>> list) {
    for(Trinity<PsiReferenceProvider,ElementPattern,Double> pair:myProviderPairs) {
      if (isAcceptable(position,pair.second)) {
        list.add(pair);
      }
    }
  }

  public void unregisterProvider(final PsiReferenceProvider provider) {
    for (final Trinity<PsiReferenceProvider, ElementPattern, Double> trinity : new ArrayList<Trinity<PsiReferenceProvider, ElementPattern, Double>>(myProviderPairs)) {
      if (trinity.first.equals(provider)) {
        myProviderPairs.remove(trinity);
      }
    }
  }

}
