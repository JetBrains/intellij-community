/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.ide;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeEvent;

public class FrameStateManagerImpl extends FrameStateManager implements ApplicationComponent,PropertyChangeListener {

  private ArrayList<FrameStateListener> myListeners = new ArrayList<FrameStateListener>();

  private boolean myShouldSynchronize;
  private final Alarm mySyncAlarm;

  @SuppressWarnings({"HardCodedStringLiteral"})
  public FrameStateManagerImpl() {

    myShouldSynchronize = false;
    mySyncAlarm = new Alarm();

    KeyboardFocusManager focusManager=KeyboardFocusManager.getCurrentKeyboardFocusManager();
    focusManager.addPropertyChangeListener("activeWindow",this);
    focusManager.addPropertyChangeListener("focusedWindow",this);
    focusManager.addPropertyChangeListener("focusOwner",this);
  }

  @NonNls
  public String getComponentName() {
    return "FrameStateManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void propertyChange(PropertyChangeEvent e) {
    KeyboardFocusManager focusManager = (KeyboardFocusManager)e.getSource();
    Window activeWindow = focusManager.getActiveWindow();

    if (activeWindow != null) {
      mySyncAlarm.cancelAllRequests();
      if (myShouldSynchronize) {
        myShouldSynchronize = false;
        fireActivationEvent();
      }
      return;
    }
    else{
      mySyncAlarm.cancelAllRequests();
      mySyncAlarm.addRequest(new Runnable() {
        public void run() {
          myShouldSynchronize = true;
          fireDeactivationEvent();
        }
      }, 200, ModalityState.NON_MODAL);
    }
  }

  private void fireDeactivationEvent() {
    final FrameStateListener[] listeners = myListeners.toArray(new FrameStateListener[myListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      FrameStateListener listener = listeners[i];
      listener.onFrameDeactivated();
    }
  }

  private void fireActivationEvent() {
    final FrameStateListener[] listeners = myListeners.toArray(new FrameStateListener[myListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      FrameStateListener listener = listeners[i];
      listener.onFrameActivated();
    }
  }

  public synchronized void addListener(FrameStateListener listener) {
    myListeners.add(listener);
  }

  public synchronized void removeListener(FrameStateListener listener) {
    myListeners.remove(listener);
  }

}
