/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

public class DefendedCopiesRefreshProxy implements CopiesRefresh {
  private final ReentranceDefence myDefence;
  
  private final MyEnsureInit myEnsureInit;
  private final MyAsynchRequest myAsynchRequest;
  private final MySynchRequest mySynchRequest;

  public DefendedCopiesRefreshProxy(final CopiesRefresh refresh) {
    myDefence = new ReentranceDefence();
    
    myEnsureInit = new MyEnsureInit(refresh);
    myAsynchRequest = new MyAsynchRequest(refresh);
    mySynchRequest = new MySynchRequest(refresh);
  }

  public void ensureInit() {
    ReentranceDefence.executeReentrant(myDefence, myEnsureInit);
  }

  public void asynchRequest() {
    ReentranceDefence.executeReentrant(myDefence, myAsynchRequest);
  }

  public void synchRequest() {
    ReentranceDefence.executeReentrant(myDefence, mySynchRequest);
  }

  public Runnable proxyRefresher(final Runnable runnable) {
    return new Runnable() {
      public void run() {
        myDefence.executeOtherDefended(runnable);
      }
    };
  }

  private static class MyAsynchRequest extends MyBase {
    private MyAsynchRequest(final CopiesRefresh delegate) {
      super(delegate);
    }

    public Boolean executeMe() {
      myDelegate.asynchRequest();
      return null;
    }
  }

  private static class MySynchRequest extends MyBase {
    private MySynchRequest(final CopiesRefresh delegate) {
      super(delegate);
    }

    public Boolean executeMe() {
      myDelegate.synchRequest();
      return null;
    }
  }

  private static class MyEnsureInit extends MyBase {
    private MyEnsureInit(final CopiesRefresh delegate) {
      super(delegate);
    }

    public Boolean executeMe() {
      myDelegate.ensureInit();
      return null;
    }
  }

  private abstract static class MyBase implements ReentranceDefence.MyControlled<Boolean> {
    protected final CopiesRefresh myDelegate;

    protected MyBase(final CopiesRefresh delegate) {
      myDelegate = delegate;
    }

    public Boolean executeMeSimple() {
      return null;
    }
  }
}
