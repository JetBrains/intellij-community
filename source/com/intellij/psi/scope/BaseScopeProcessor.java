package com.intellij.psi.scope;

public abstract class BaseScopeProcessor implements PsiScopeProcessor{

  public <T> T getHint(Class<T> hintClass) {
    if (hintClass.isAssignableFrom(this.getClass())){
      return (T)this;
    }
    else{
      return null;
    }
  }

  public void handleEvent(PsiScopeProcessor.Event event, Object associated){}
}
