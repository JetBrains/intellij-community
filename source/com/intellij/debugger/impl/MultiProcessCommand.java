package com.intellij.debugger.impl;

import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.engine.managerThread.DebuggerCommand;
import com.intellij.openapi.util.Pair;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */

public class MultiProcessCommand implements Runnable{
  private final List<Pair<DebugProcessImpl,  DebuggerCommandImpl>> myCommands = new ArrayList<Pair<DebugProcessImpl, DebuggerCommandImpl>>();

  public void run() {
    for(;;) {
      Pair<DebugProcessImpl,  DebuggerCommandImpl> pair;

      synchronized(myCommands) {
        if(myCommands.isEmpty()) break;

        pair = myCommands.get(0);
      }

      pair.getFirst().getManagerThread().invokeAndWait(pair.getSecond());

      synchronized(myCommands) {
        if(myCommands.isEmpty()) break;

        myCommands.remove(0);
      }
    }
  }

  public void terminate() {
    synchronized(myCommands) {
      if(myCommands.isEmpty()) return;
      Pair<DebugProcessImpl,  DebuggerCommandImpl> pair = myCommands.get(0);
      pair.getFirst().getManagerThread().terminateCommand(pair.getSecond());
      myCommands.clear();
    }
  }

  public void addCommand(DebugProcessImpl debugProcess, DebuggerCommandImpl command) {
    myCommands.add(new Pair<DebugProcessImpl, DebuggerCommandImpl>(debugProcess, command));
  }
}
