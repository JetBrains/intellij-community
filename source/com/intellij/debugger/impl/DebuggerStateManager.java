package com.intellij.debugger.impl;

import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.debugger.DebuggerInvocationUtil;

import java.util.Iterator;
import java.util.LinkedList;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jun 4, 2003
 * Time: 12:45:56 PM
 * To change this template use Options | File Templates.
 */
public abstract class DebuggerStateManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.debugger.impl.DebuggerStateManager");
  private LinkedList myListeners = new LinkedList();
  private boolean myCopyListeners = false;

  public abstract DebuggerContextImpl getContext();
  
  public abstract void setState(DebuggerContextImpl context, int state, int event, String description);

  //we allow add listeners inside DebuggerContextListener.changeEvent
  public void addPriorityListener(DebuggerContextListener listener){
    if(myCopyListeners) {
      myListeners = new LinkedList(myListeners);
      myCopyListeners = false;
    }
    myListeners.addFirst(listener);
  }

  //we allow add listeners inside DebuggerContextListener.changeEvent
  public void addListener(DebuggerContextListener listener){
    if(myCopyListeners) {
      myListeners = new LinkedList(myListeners);
      myCopyListeners = false;
    }
    myListeners.add(listener);
  }

  //we allow remove listeners inside DebuggerContextListener.changeEvent
  public void removeListener(DebuggerContextListener listener){
    if(myCopyListeners) {
      myListeners = new LinkedList(myListeners);
      myCopyListeners = false;
    }
    myListeners.remove(listener);
  }

  protected void fireStateChanged(DebuggerContextImpl newContext, int event) {
    myCopyListeners = true;
    for (Iterator iterator = myListeners.iterator(); iterator.hasNext();) {
      DebuggerContextListener debuggerContextListener = (DebuggerContextListener) iterator.next();
      try {
        debuggerContextListener.changeEvent(newContext, event);
      }
      catch(Throwable th) {
        LOG.error(th);
      }
    }
    myCopyListeners = false;
  }
}
