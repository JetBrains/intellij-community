package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.PsiReferenceProvider;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.scope.util.PsiScopesUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 27.03.2003
 * Time: 17:23:43
 * To change this template use Options | File Templates.
 */
public abstract class GenericReferenceProvider implements PsiReferenceProvider{
  protected static final GenericReference[] EMPTY_ARRAY = new GenericReference[0];

  public void handleEmptyContext(PsiScopeProcessor processor, PsiElement position){
    PsiScopesUtil.treeWalkUp(processor, position, null);
  }

  private boolean mySoft = false;

  public void setSoft(boolean softFlag){
    mySoft = softFlag;
  }

  public boolean isSoft(){
    return mySoft;
  }
}
