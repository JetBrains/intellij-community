// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Processor;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.idea.svn.api.ErrorCode;
import org.jetbrains.idea.svn.commandLine.SvnBindException;

public abstract class RepeatSvnActionThroughBusy {
  public static final int REPEAT = 10;

  public static final Processor<Exception> ourBusyExceptionProcessor = e -> {
    if (e instanceof SvnBindException) {
      return ((SvnBindException)e).contains(ErrorCode.WC_LOCKED);
    }
    return false;
  };

  protected int myCnt = REPEAT;
  protected long myTimeout = 50;

  protected abstract void executeImpl() throws VcsException;

  protected Object myT;

  public <T> T compute() throws VcsException {
    execute();
    return (T) myT;
  }

  public void execute() throws VcsException {
    while (true) {
      try {
        executeImpl();
        break;
      }
      catch (VcsException e) {
        if (ourBusyExceptionProcessor.process(e)) {
          if (myCnt > 0) {
            TimeoutUtil.sleep(myTimeout * (REPEAT - myCnt + 1));
            -- myCnt;
            continue;
          }
        }
        throw e;
      }
    }
  }
}
