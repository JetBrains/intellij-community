package com.intellij.debugger.impl;

import com.intellij.debugger.impl.DebuggerContextImpl;


/**
 * Created by IntelliJ IDEA.
 * User: lex
 * Date: Jun 26, 2003
 * Time: 11:41:29 PM
 * To change this template use Options | File Templates.
 */
public interface DebuggerContextListener {

  void changeEvent(DebuggerContextImpl newContext, int event);
}
