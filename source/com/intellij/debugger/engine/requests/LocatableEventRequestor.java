package com.intellij.debugger.engine.requests;

import com.intellij.debugger.engine.events.SuspendContextCommandImpl;
import com.intellij.debugger.requests.Requestor;
import com.sun.jdi.event.LocatableEvent;

/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jun 27, 2003
 * Time: 8:05:52 PM
 * To change this template use Options | File Templates.
 */
public interface LocatableEventRequestor extends Requestor {
  /**
   * returns whether should resume 
   */ 
  boolean processLocatableEvent(SuspendContextCommandImpl action, LocatableEvent event);
}
