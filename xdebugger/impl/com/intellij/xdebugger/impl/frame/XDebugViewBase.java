package com.intellij.xdebugger.impl.frame;

import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionAdapter;
import com.intellij.openapi.Disposable;

/**
 * @author nik
 */
public abstract class XDebugViewBase implements Disposable {
  protected final XDebugSession mySession;
  private MyDebugSessionListener mySessionListener;

  public XDebugViewBase(final XDebugSession session, Disposable parentDisposable) {
    mySession = session;
    mySessionListener = new MyDebugSessionListener();
    mySession.addSessionListener(mySessionListener);
  }

  protected abstract void rebuildView();

  public void dispose() {
    mySession.removeSessionListener(mySessionListener);
  }

  private class MyDebugSessionListener extends XDebugSessionAdapter {

    public void sessionPaused() {
      rebuildView();
    }

    public void sessionResumed() {
      rebuildView();
    }

  }
}
