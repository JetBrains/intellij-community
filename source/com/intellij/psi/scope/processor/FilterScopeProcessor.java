package com.intellij.psi.scope.processor;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.ResolveState;
import com.intellij.psi.filters.ElementFilter;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.util.SmartList;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 13.02.2003
 * Time: 15:21:27
 * To change this template use Options | File Templates.
 */
public class FilterScopeProcessor<T> extends BaseScopeProcessor{
  protected final SmartList<T> myResults;
  private PsiElement myCurrentDeclarationHolder;
  private final ElementFilter myFilter;
  private final PsiScopeProcessor myProcessor;

  public FilterScopeProcessor(ElementFilter filter, PsiScopeProcessor processor, SmartList<T> container){
    myFilter = filter;
    myProcessor = processor;
    myResults = container;
  }

  public FilterScopeProcessor(ElementFilter filter, SmartList<T> container){
    this(filter, null, container);
  }

  public FilterScopeProcessor(ElementFilter filter, PsiScopeProcessor proc){
    this(filter, proc, new SmartList<T>());
  }

  public FilterScopeProcessor(ElementFilter filter){
    this(filter, null, new SmartList<T>());
  }

  public void handleEvent(Event event, Object associated){
    if(myProcessor != null){
      myProcessor.handleEvent(event, associated);
    }
    if(event == Event.SET_DECLARATION_HOLDER && associated instanceof PsiElement){
      myCurrentDeclarationHolder = (PsiElement)associated;
    }
  }

  public boolean execute(PsiElement element, ResolveState state){
    if (myFilter.isAcceptable(element, myCurrentDeclarationHolder)){
      if(myProcessor != null){
        return myProcessor.execute(element, state);
      }
      add(element, state.get(PsiSubstitutor.KEY));
    }
    return true;
  }

  protected void add(PsiElement element, PsiSubstitutor substitutor){
    myResults.add((T)element);
  }

  public SmartList<T> getResults(){
    return myResults;
  }
}
