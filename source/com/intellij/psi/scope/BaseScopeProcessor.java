package com.intellij.psi.scope;

import com.intellij.util.ReflectionCache;

public abstract class BaseScopeProcessor implements PsiScopeProcessor{

  public <T> T getHint(Class<T> hintClass) {
    if (ReflectionCache.isAssignable(hintClass, this.getClass())){
      return (T)this;
    }
    else{
      return null;
    }
  }

  public void handleEvent(PsiScopeProcessor.Event event, Object associated){}
}
