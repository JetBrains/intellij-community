package com.intellij.psi.scope.processor;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.scope.BaseScopeProcessor;
import com.intellij.psi.scope.ElementClassHint;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: igork
 * Date: Dec 12, 2002
 * Time: 4:06:54 PM
 * To change this template use Options | File Templates.
 */
public abstract class VariablesProcessor extends BaseScopeProcessor implements ElementClassHint {
  private boolean myStaticScopeFlag = false;
  private final boolean myStaticSensitiveFlag;
  private final List myResultList;

  /** Collecting _all_ variables in scope */
  public VariablesProcessor(boolean staticSensitive){
    this(staticSensitive, new ArrayList());
  }

  /** Collecting _all_ variables in scope */
  public VariablesProcessor(boolean staticSensitive, List list){
    myStaticSensitiveFlag = staticSensitive;
    myResultList = list;
  }

  protected abstract boolean check(PsiVariable var, PsiSubstitutor substitutor);

  public boolean shouldProcess(Class elementClass) {
    return PsiVariable.class.isAssignableFrom(elementClass);
  }

  /** Always return true since we wanna get all vars in scope */
  public boolean execute(PsiElement pe, PsiSubstitutor substitutor){
    final boolean ret = true;

    if(pe instanceof PsiVariable){
      final PsiVariable pvar = (PsiVariable)pe;
      if(!myStaticSensitiveFlag || (!myStaticScopeFlag || pvar.hasModifierProperty(PsiModifier.STATIC))){
        if(check(pvar, substitutor)){
          myResultList.add(pvar);
        }
      }
    }

    return ret;
  }

  public final void handleEvent(Event event, Object associated){
    if(event == Event.START_STATIC)
      myStaticScopeFlag = true;
  }

  public int size(){
    return myResultList.size();
  }

  public PsiVariable getResult(int i){
    return (PsiVariable)myResultList.get(i);
  }
  /** sometimes it is important to get results as array */
  public PsiVariable[] getResultsAsArray(){
    PsiVariable[] ret = new PsiVariable[myResultList.size()];
    myResultList.toArray(ret);
    return ret;
  }
}
