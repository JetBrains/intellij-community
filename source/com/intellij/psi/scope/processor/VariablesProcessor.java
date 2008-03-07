package com.intellij.psi.scope.processor;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.util.ReflectionCache;
import com.intellij.util.SmartList;

import java.util.List;

public abstract class VariablesProcessor extends BaseScopeProcessor implements ElementClassHint {
  private boolean myStaticScopeFlag = false;
  private final boolean myStaticSensitiveFlag;
  private final List<PsiVariable> myResultList;

  /** Collecting _all_ variables in scope */
  public VariablesProcessor(boolean staticSensitive){
    this(staticSensitive, new SmartList<PsiVariable>());
  }

  /** Collecting _all_ variables in scope */
  public VariablesProcessor(boolean staticSensitive, List<PsiVariable> list){
    myStaticSensitiveFlag = staticSensitive;
    myResultList = list;
  }

  protected abstract boolean check(PsiVariable var, ResolveState state);

  public boolean shouldProcess(Class elementClass) {
    return ReflectionCache.isAssignable(PsiVariable.class, elementClass);
  }

  /** Always return true since we wanna get all vars in scope */
  public boolean execute(PsiElement pe, ResolveState state){
    if(pe instanceof PsiVariable){
      final PsiVariable pvar = (PsiVariable)pe;
      if(!myStaticSensitiveFlag || !myStaticScopeFlag || pvar.hasModifierProperty(PsiModifier.STATIC)){
        if(check(pvar, state)){
          myResultList.add(pvar);
        }
      }
    }
    return true;
  }

  public final void handleEvent(Event event, Object associated){
    if(event == JavaScopeProcessorEvent.START_STATIC)
      myStaticScopeFlag = true;
  }

  public int size(){
    return myResultList.size();
  }

  public PsiVariable getResult(int i){
    return myResultList.get(i);
  }
}
