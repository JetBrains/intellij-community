package com.intellij.psi.impl.source.resolve.reference;

import com.intellij.psi.PsiElement;
import com.intellij.psi.filters.ElementFilter;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 01.04.2003
 * Time: 16:52:28
 * To change this template use Options | File Templates.
 */
public class SimpleProviderBinding implements ProviderBinding {
  private final ElementFilter myPosition;
  private final List myScopes = new ArrayList();
  private final List myProviders = new ArrayList();

  public SimpleProviderBinding(ElementFilter filter, Class scope){
    myScopes.add(scope);
    myPosition = filter;
  }

  public SimpleProviderBinding(Class scope){
    this(null, scope);
  }

  public SimpleProviderBinding(){
    myPosition = null;
  }

  private boolean isAcceptable(PsiElement position){
    if(position == null) return false;
    final Iterator iter = myScopes.iterator();
    while(iter.hasNext()){
      final Class scopeClass = (Class) iter.next();
      if(scopeClass.isAssignableFrom(position.getClass())){
        return myPosition == null || myPosition.isAcceptable(position, position);
      }
    }
    return false;
  }

  public void registerProvider(PsiReferenceProvider provider){
    myProviders.add(provider);
  }

  public PsiReferenceProvider[] getAcceptableProviders(PsiElement position) {
    if (isAcceptable(position)) return getProviders();
    return null;
  }

  public PsiReferenceProvider[] getProviders(){
    return (PsiReferenceProvider[]) myProviders.toArray(new PsiReferenceProvider[myProviders.size()]);
  }
}
