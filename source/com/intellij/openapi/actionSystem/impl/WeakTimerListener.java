package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.actionSystem.ex.TimerListener;
import com.intellij.openapi.application.ModalityState;

import java.lang.ref.WeakReference;

/**
 * @author Vladimir Kondratyev
 */
public class WeakTimerListener implements TimerListener {
  private ActionManagerEx myManager;
  private WeakReference<TimerListener> myRef;

  public WeakTimerListener(ActionManagerEx manager, TimerListener delegate) {
    myManager = manager;
    myRef = new WeakReference<TimerListener>(delegate);
  }

  public ModalityState getModalityState() {
    TimerListener delegate = myRef.get();
    if (delegate != null) {
      return delegate.getModalityState();
    }
    else{
      myManager.removeTimerListener(this);
      return null;
    }
  }

  public void run() {
    TimerListener delegate = myRef.get();
    if (delegate != null) {
      delegate.run();
    }
  }
}
