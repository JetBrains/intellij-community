package com.intellij.psi.scope.processor;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 13.02.2003
 * Time: 15:21:27
 * To change this template use Options | File Templates.
 */
public class FilterScopeProcessor extends BaseScopeProcessor
 implements ElementClassHint{
  private final PsiElement myElement;
  protected final List myResults;
  private PsiElement myCurrentDeclarationHolder;
  private final ElementFilter myFilter;
  private final PsiScopeProcessor myProcessor;

  public FilterScopeProcessor(ElementFilter filter, PsiElement element, PsiScopeProcessor processor, List container){
    myFilter = filter;
    myElement = element;
    myProcessor = processor;
    myResults = container;
  }


  public FilterScopeProcessor(ElementFilter filter, PsiElement element, List container){
    this(filter, element, null, container);
  }

  public FilterScopeProcessor(ElementFilter filter, PsiScopeProcessor processor){
    this(filter, null, processor, null);
  }

  public FilterScopeProcessor(ElementFilter filter, PsiElement element, PsiScopeProcessor proc){
    this(filter, element, proc, new ArrayList());
  }


  public FilterScopeProcessor(ElementFilter filter, PsiElement element){
    this(filter, element, null, new ArrayList());
  }

  public void handleEvent(Event event, Object associated){
    if(myProcessor != null){
      myProcessor.handleEvent(event, associated);
    }
    if(event == Event.SET_DECLARATION_HOLDER && associated instanceof PsiElement){
      myCurrentDeclarationHolder = (PsiElement)associated;
    }
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor){
    if (myFilter.isAcceptable(element, myCurrentDeclarationHolder)){
      if(myProcessor != null){
        return myProcessor.execute(element, substitutor);
      }
      add(element, substitutor);
    }
    return true;
  }

  public PsiElement getPlace(){
    return myElement;
  }

  protected void add(PsiElement element, PsiSubstitutor substitutor){
    myResults.add(element);
  }

  public List getResults(){
    return myResults;
  }

  public boolean shouldProcess(Class elementClass){
    return myFilter.isClassAcceptable(elementClass);
  }
}
