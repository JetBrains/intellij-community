/*
 * Copyright (c) 2004 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.pom.core.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.pom.*;
import com.intellij.pom.event.PomModelEvent;
import com.intellij.pom.event.PomModelListener;
import com.intellij.util.IncorrectOperationException;
import com.intellij.psi.PsiLock;

import java.util.*;

public class PomModelImpl extends UserDataHolderBase implements PomModel {
  private final PomProject myPomProject;
  private Map<Class<? extends PomModelAspect>, PomModelAspect> myAspects = new HashMap<Class<? extends PomModelAspect>, PomModelAspect>();
  private Map<PomModelAspect, Set<PomModelAspect>> myAspectDependencies = new HashMap<PomModelAspect, Set<PomModelAspect>>();
  private Map<PomModelAspect, List<PomModelAspect>> myIncidence = new HashMap<PomModelAspect, List<PomModelAspect>>();
  private Map<PomModelAspect, List<PomModelAspect>> myInvertedIncidence = new HashMap<PomModelAspect, List<PomModelAspect>>();
  private List<PomModelListener> myListeners = new ArrayList<PomModelListener>();

  public PomModelImpl(Project project) {
    myPomProject = new PomProjectImpl(this, project);
  }

  public PomProject getRoot() {
    return myPomProject;
  }

  public <T extends PomModelAspect> T getModelAspect(Class<T> aClass) {
    return (T)myAspects.get(aClass);
  }

  public void registerAspect(PomModelAspect aspect, Set<PomModelAspect> dependencies) {
    myAspectDependencies.put(aspect, dependencies);
    myAspects.put(aspect.getClass(), aspect);
    final Iterator<PomModelAspect> iterator = dependencies.iterator();
    final List<PomModelAspect> deps = new ArrayList<PomModelAspect>();
    // todo: reorder dependencies
    while (iterator.hasNext()) {
      final PomModelAspect depend = iterator.next();
      deps.add(depend);
      deps.addAll(getAllDependencies(depend));
    }

    final Iterator<PomModelAspect> depsIterator = deps.iterator();
    while (depsIterator.hasNext()) {
      final PomModelAspect pomModelAspect = depsIterator.next();
      final List<PomModelAspect> pomModelAspects = myInvertedIncidence.get(pomModelAspect);
      if(pomModelAspects != null) pomModelAspects.add(aspect);
      else myInvertedIncidence.put(pomModelAspect, new ArrayList<PomModelAspect>(Collections.singletonList(aspect)));
    }
    myIncidence.put(aspect, deps);
  }

  //private final Pair<PomModelAspect, PomModelAspect> myHolderPair = new Pair<PomModelAspect, PomModelAspect>(null, null);
  public List<PomModelAspect> getAllDependencies(PomModelAspect aspect){
    List<PomModelAspect> pomModelAspects = myIncidence.get(aspect);
    return pomModelAspects != null ? pomModelAspects : Collections.EMPTY_LIST;
  }

  public List<PomModelAspect> getAllDependants(PomModelAspect aspect){
    List<PomModelAspect> pomModelAspects = myInvertedIncidence.get(aspect);
    return pomModelAspects != null ? pomModelAspects : Collections.EMPTY_LIST;
  }

  public void addModelListener(PomModelListener listener) {
    synchronized(myListeners){
      myListeners.add(listener);
    }
  }

  public void removeModelListener(PomModelListener listener) {
    synchronized(myListeners){
      myListeners.remove(listener);
    }
  }

  private final Stack<PomModelAspect> myBlockedAspects = new Stack<PomModelAspect>();

  public synchronized void runTransaction(PomTransaction transaction, PomModelAspect aspect) throws IncorrectOperationException{
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if(progressIndicator != null) progressIndicator.startNonCancelableSection();
    try{
    synchronized(PsiLock.LOCK){
      myBlockedAspects.push(aspect);
      final PomModelEvent event;
      try{
        event = transaction.run();
        if(event == null) return;
      }
      catch(IncorrectOperationException ioe){
        return;
      }
      finally{
        myBlockedAspects.pop();
      }

      final List<PomModelAspect> dependants = getAllDependants(aspect);
      { // update
        final Iterator<PomModelAspect> depsIter = dependants.iterator();
        while (depsIter.hasNext()) {
          final PomModelAspect modelAspect = depsIter.next();
          if(myBlockedAspects.contains(modelAspect)) continue;
          modelAspect.update(event);
        }
      }
      {
        final Iterator<PomModelListener> listenersIterator = myListeners.iterator();
        while (listenersIterator.hasNext()) listenersIterator.next().modelChanged(event);
      }
    }
    }
    finally{
      if(progressIndicator != null) progressIndicator.finishNonCancelableSection();
    }
  }
  // Project component

  public void projectOpened() {}
  public void projectClosed() {}
  public String getComponentName() {
    return "PomModel";
  }
  public void initComponent() {}
  public void disposeComponent() {}
}
