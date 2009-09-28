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
