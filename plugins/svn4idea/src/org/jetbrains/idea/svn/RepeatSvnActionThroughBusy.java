/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.svn;

import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.Processor;
import com.intellij.util.TimeoutUtil;
import org.tmatesoft.sqljet.core.SqlJetErrorCode;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNException;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 6/29/12
 * Time: 3:45 PM
 */
public abstract class RepeatSvnActionThroughBusy {
  public static final int REPEAT = 10;

  public static final Processor<Exception> ourBusyExceptionProcessor = new Processor<Exception>() {
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    @Override
    public boolean process(Exception e) {
      if (e instanceof SVNException) {
        final SVNErrorCode errorCode = ((SVNException)e).getErrorMessage().getErrorCode();
        if (SVNErrorCode.WC_LOCKED.equals(errorCode)) {
          return true;
        }
        else if (SVNErrorCode.SQLITE_ERROR.equals(errorCode)) {
          Throwable cause = ((SVNException)e).getErrorMessage().getCause();
          if (cause instanceof SqlJetException) {
            return SqlJetErrorCode.BUSY.equals(((SqlJetException)cause).getErrorCode());
          }
        }
      }
      return false;
    }
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
