/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.ui.update;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.Disposable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class MergingUpdateQueue implements ActionListener, Disposable {

  private boolean myActive;

  private final Set<Update> mySheduledUpdates = new TreeSet<Update>();

  private Timer myWaiterForMerge;

  private boolean myFlushing;

  private String myName;
  private ModalityState myModalityState;

  public MergingUpdateQueue(String name, int mergingTimeSpan, boolean isActive, ModalityState activeState) {
    myWaiterForMerge = new Timer(mergingTimeSpan, this);
    myName = name;
    myModalityState = activeState;

    if (isActive) {
      showNotify();
    }
  }

  public void setMergingTimeSpan(int timeSpan) {
    myWaiterForMerge.setDelay(timeSpan);
  }
  
  public void cancelAllUpdates() {
    synchronized(mySheduledUpdates) {
      mySheduledUpdates.clear();
    }
  }
  
  
  public void hideNotify() {
    if (!myActive) {
      return;
    }

    myActive = false;
    myWaiterForMerge.stop();
  }

  public void showNotify() {
    if (myActive) {
      return;
    }

    myWaiterForMerge.start();
    myActive = true;
    flush();
  }


  public void actionPerformed(ActionEvent e) {
    flush();
  }

  public void flush() {
    if (mySheduledUpdates.size() > 0) {
      flush(true);
    }
  }

  public boolean isFlushing() {
    return myFlushing;
  }

  public void flush(boolean invokeLaterIfNotDispatch) {
    if (myFlushing) return;
    if (!isModalityStateCorrect()) return;

    myFlushing = true;
    final Runnable toRun = new Runnable() {
      public void run() {
        try {
          List<Update> toUpdate;
          final Update[] all;
          
          synchronized(mySheduledUpdates) {
            toUpdate = new ArrayList<Update>(mySheduledUpdates.size());
            all = mySheduledUpdates.toArray(new Update[mySheduledUpdates.size()]);
            mySheduledUpdates.clear();
          }

          for (int i = 0; i < all.length; i++) {
            Update each = all[i];
            if (!isExpired(each)) {
              toUpdate.add(each);
            }
            each.setProcessed();
          }
          
          execute(toUpdate.toArray(new Update[toUpdate.size()]));
        } 
        finally {
          myFlushing = false;
        }
      }
    };

    if (invokeLaterIfNotDispatch && !ApplicationManager.getApplication().isDispatchThread()) {
      SwingUtilities.invokeLater(toRun);
    } 
    else {
      toRun.run();
    }
  }

  private boolean isModalityStateCorrect() {
    ModalityState current = ApplicationManager.getApplication().getCurrentModalityState();
    return myModalityState.equals(current) || myModalityState.dominates(current);
  }

  private boolean isExpired(Update each) {
    return each.isDisposed() || each.isExpired();
  }

  protected void execute(final Update[] update) {
    for (int i = 0; i < update.length; i++) {
      final Update each = update[i];
      if (each.executeInWriteAction()) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            each.run();
          }
        });
      } 
      else {
        each.run();
      }
    }
  }

  public final void queue(Update update) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      update.run();
      return;
    }
    
    synchronized(mySheduledUpdates) {
      boolean updateWasEatenByQueue = eatThisOrOthers(update);
      if (updateWasEatenByQueue) return;

      if (myActive) {
        put(update);
        if (mySheduledUpdates.isEmpty()) {
          myWaiterForMerge.restart();
        }
      } else {
        put(update);
      }
    }
  }

  private boolean eatThisOrOthers(Update update) {
    if (mySheduledUpdates.contains(update)) {
      return false;
    }

    final Update[] updates = mySheduledUpdates.toArray(new Update[mySheduledUpdates.size()]);
    for (int i = 0; i < updates.length; i++) {
      Update eachInQueue = updates[i];
      if (eachInQueue.canEat(update)) {
        return true;
      }
      if (update.canEat(eachInQueue)) {
        mySheduledUpdates.remove(eachInQueue);
      }
    }
    return false;
  }

  public final void run(Update update) {
    execute(new Update[] {update});
  }

  private void put(Update update) {
    mySheduledUpdates.remove(update);
    mySheduledUpdates.add(update);
  }

  protected boolean passThroughForUnitTesting() {
    return true;
  }

  public boolean isActive() {
    return myActive;
  }

  public void dispose() {
    myWaiterForMerge.stop();
  }

  public String toString() {
    return "Merger: " + myName + " active=" + myActive + " sheduled=" + mySheduledUpdates;
  }

  public boolean containsUpdateOf(int priority) {
    Update[] update = mySheduledUpdates.toArray(new Update[mySheduledUpdates.size()]);
    for (int i = 0; i < update.length; i++) {
      Update each = update[i];
      if (each.getPriority() == priority) {
        return true;
      }
    }
    return false;
  }
}
