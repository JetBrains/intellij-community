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
  private PomModelEvent myAccumulatedEvent;
  public PomTransactionBase(PsiElement scope){
    myScope = scope;
    myAccumulatedEvent = new PomModelEvent(scope.getManager().getProject().getModel());
  }

  public PomModelEvent getAccumulatedEvent() {
    return myAccumulatedEvent;
  }

  public void run() throws IncorrectOperationException {
    // override accumulated event because transaction should construct full model event in its aspect
    final PomModelEvent event = runInner();
    if(event == null) return;
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
}
