package com.intellij.xdebugger.impl.frame;

import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionAdapter;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
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

  private void onSessionEvent(final boolean onlyFrameChanged) {
    DebuggerUIUtil.invokeOnEventDispatch(new Runnable() {
      public void run() {
        rebuildView(onlyFrameChanged);
      }
    });
  }

  protected abstract void rebuildView(final boolean onlyFrameChanged);

  public void dispose() {
    mySession.removeSessionListener(mySessionListener);
  }

  private class MyDebugSessionListener extends XDebugSessionAdapter {
    public void sessionPaused() {
      onSessionEvent(false);
    }

    public void sessionResumed() {
      onSessionEvent(false);
    }

    public void sessionStopped() {
      onSessionEvent(false);
    }

    public void stackFrameChanged() {
      onSessionEvent(true);
    }
  }
}
