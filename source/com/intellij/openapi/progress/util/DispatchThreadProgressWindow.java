package com.intellij.openapi.progress.util;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;

import java.awt.*;

public class DispatchThreadProgressWindow extends ProgressWindow{
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.util.DispatchThreadProgressWindow");

  private long myLastPumpEventsTime = 0;
  private static final int PUMP_INTERVAL = SystemInfo.isWindows ? 100 : 500;

  public DispatchThreadProgressWindow(boolean shouldShowCancel, Project project) {
    super(shouldShowCancel, project);
  }

  public void setText(String text) {
    super.setText(text);
    pumpEvents();
  }

  public void setFraction(double fraction) {
    super.setFraction(fraction);
    pumpEvents();
  }

  public void setText2(String text) {
    super.setText2(text);
    pumpEvents();
  }

  private void pumpEvents() {
    long time = System.currentTimeMillis();
    if (time - myLastPumpEventsTime < PUMP_INTERVAL) return;
    myLastPumpEventsTime = time;

    IdeEventQueue eventQueue = IdeEventQueue.getInstance();
    while(true){
      AWTEvent event = eventQueue.peekEvent();
      if (event == null) return;
      try{
        AWTEvent event1 = eventQueue.getNextEvent();
        eventQueue.dispatchEvent(event1);
      }
      catch(Exception e){
        LOG.error(e); //?
      }
    }
  }
}
