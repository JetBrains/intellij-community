/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.pom.impl;

import com.intellij.pom.PomModelAspect;
import com.intellij.pom.PomTransaction;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;

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
    for (PomModelAspect aspect : event.getChangedAspects()) {
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
