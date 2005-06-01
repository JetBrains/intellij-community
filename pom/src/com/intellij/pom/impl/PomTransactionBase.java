package com.intellij.pom.impl;

import com.intellij.pom.PomTransaction;
import com.intellij.pom.PomModelAspect;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;

import java.util.Set;
import java.util.Iterator;

public abstract class PomTransactionBase implements PomTransaction{
  private PsiElement myScope;
  private PomModelAspect myAspect;
  private PomModelEvent myAccumulatedEvent;
  public PomTransactionBase(PsiElement scope, final PomModelAspect aspect){
    myScope = scope;
    myAspect = aspect;
    myAccumulatedEvent = new PomModelEvent(scope.getManager().getProject().getModel());
  }

  public PomModelEvent getAccumulatedEvent() {
    return myAccumulatedEvent;
  }

  public void run() throws IncorrectOperationException {
    // override accumulated event because transaction should construct full model event in its aspect
    final PomModelEvent event = runInner();
    if(event == null){
      // in case of null event aspect change set supposed to be rebuild by low level events
      myAccumulatedEvent.registerChangeSet(myAspect, null);
      return;
    }
    final Set<PomModelAspect> changedAspects = event.getChangedAspects();
    final Iterator<PomModelAspect> iterator = changedAspects.iterator();
    while (iterator.hasNext()) {
      final PomModelAspect aspect = iterator.next();
      myAccumulatedEvent.registerChangeSet(aspect, event.getChangeSet(aspect));
    }
  }

  public abstract PomModelEvent runInner() throws IncorrectOperationException;

  public PsiElement getChangeScope() {
    return myScope;
  }

  public PomModelAspect getTransactionAspect() {
    return myAspect;
  }
}
