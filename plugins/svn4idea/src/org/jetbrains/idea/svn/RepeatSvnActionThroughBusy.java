/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import org.tmatesoft.svn.core.SVNException;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 6/29/12
 * Time: 3:45 PM
 */
public abstract class RepeatSvnActionThroughBusy {
  public static final int REPEAT = 10;
  protected int myCnt = REPEAT;
  protected long myTimeout = 50;
  protected abstract void executeImpl() throws SVNException;
  protected Object myT;

  public <T> T compute() throws SVNException {
    execute();
    return (T) myT;
  }

  public void execute() throws SVNException {
    while (true) {
      try {
        executeImpl();
        break;
      } catch (SVNException e) {
        if (SvnVcs.ourBusyExceptionProcessor.process(e)) {
          if (myCnt > 0) {
            try {
              Thread.sleep(myTimeout * (REPEAT - myCnt + 1));
            }
            catch (InterruptedException e1) {
              //
            }
            -- myCnt;
            continue;
          }
        }
        throw e;
      }
    }
  }
}
