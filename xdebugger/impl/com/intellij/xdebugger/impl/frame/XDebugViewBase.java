package com.intellij.xdebugger.impl.frame;

import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebugSessionAdapter;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.openapi.Disposable;

/**
 * @author nik
 */
public abstract class XDebugViewBase implements Disposable {
  protected enum SessionEvent {PAUSED, BEFORE_RESUME, RESUMED, STOPPED, FRAME_CHANGED}
  protected final XDebugSession mySession;
  private MyDebugSessionListener mySessionListener;

  public XDebugViewBase(final XDebugSession session, Disposable parentDisposable) {
    mySession = session;
    mySessionListener = new MyDebugSessionListener();
    mySession.addSessionListener(mySessionListener);
  }

  private void onSessionEvent(final SessionEvent event) {
    DebuggerUIUtil.invokeOnEventDispatch(new Runnable() {
      public void run() {
        rebuildView(event);
      }
    });
  }

  protected abstract void rebuildView(final SessionEvent event);

  public void dispose() {
    mySession.removeSessionListener(mySessionListener);
  }

  private class MyDebugSessionListener extends XDebugSessionAdapter {
    public void sessionPaused() {
      onSessionEvent(SessionEvent.PAUSED);
    }

    public void sessionResumed() {
      onSessionEvent(SessionEvent.RESUMED);
    }

    public void sessionStopped() {
      onSessionEvent(SessionEvent.STOPPED);
    }

    public void stackFrameChanged() {
      onSessionEvent(SessionEvent.FRAME_CHANGED);
    }

    public void beforeSessionResume() {
      onSessionEvent(SessionEvent.BEFORE_RESUME);
    }
  }
}
