package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.impl.source.resolve.reference.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.scope.processor.ConflictFilterProcessor;
import com.intellij.psi.scope.processor.FilterScopeProcessor;
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.03.2003
 * Time: 17:33:24
 * To change this template use Options | File Templates.
 */
public abstract class GenericReference implements PsiReference{
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.reference.impl.GenericReference");
  public static final GenericReference[] EMPTY_ARRAY = new GenericReference[0];
  private final PsiReferenceProvider myProvider;

  public boolean isSoft(){
    return false;
  }

  public GenericReference(final PsiReferenceProvider provider){
    myProvider = provider;
  }

  public PsiElement resolve(){
    final PsiManager manager = getElement().getManager();
    if(manager instanceof PsiManagerImpl){
      return ((PsiManagerImpl)manager).getResolveCache().resolveWithCaching(this, new ResolveCache.Resolver() {
        public PsiElement resolve(PsiReference ref, boolean incompleteCode) {
          return resolveInner();
        }
      }, false, false);
    }
    return resolveInner();
  }

  public PsiElement resolveInner(){
    final List resultSet = new ArrayList();
    final ConflictFilterProcessor processor;
    try{
      processor = ProcessorRegistry.getInstance().getProcessorByType(getType(), resultSet, needToCheckAccessibility() ? getElement() : null);
      processor.setName(getCanonicalText());
    }
    catch(ProcessorRegistry.IncompartibleReferenceTypeException e){
      LOG.error(e);
      return null;
    }

    processVariants(processor);
    final ResolveResult[] result = processor.getResult();
    if(result.length != 1) return null;
    return result[0].getElement();
  }

  public boolean isReferenceTo(final PsiElement element){
    return element.getManager().areElementsEquivalent(element, resolve());
  }

  public Object[] getVariants(){
    final List ret = new ArrayList();
    final FilterScopeProcessor proc;
    try{
      proc = ProcessorRegistry.getInstance().getProcessorByType(getSoftenType(), ret, needToCheckAccessibility() ? getElement() : null);
    }
    catch(ProcessorRegistry.IncompartibleReferenceTypeException e){
      LOG.error(e);
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
    processVariants(proc);

    return ret.toArray();
  }

  public void processVariants(final PsiScopeProcessor processor){
    final PsiElement context = getContext();
    if(context != null){
      PsiScopesUtil.processScope(context, processor, PsiSubstitutor.EMPTY, getElement(), getElement());
    }
    else if(getContextReference() == null){
      myProvider.handleEmptyContext(processor, getElement());
    }
  }

  protected ElementManipulator getManipulator(PsiElement currentElement){
    return ReferenceProvidersRegistry.getInstance(currentElement.getProject()).getManipulator(currentElement);
  }

  public String getUnresolvedMessage(){
    return getType().getUnresolvedMessage();
  }

  public abstract PsiElement getContext();
  public abstract PsiReference getContextReference();
  public abstract ReferenceType getType();
  public abstract ReferenceType getSoftenType();
  public abstract boolean needToCheckAccessibility();
}
