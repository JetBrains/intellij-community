/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class PatchedWeakReference extends WeakReference{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.PatchedWeakReference");

  private static ArrayList ourRefsList = new ArrayList();
  private static ReferenceQueue ourQueue = new ReferenceQueue();
  private static Timer ourTimer = null;

  public PatchedWeakReference(Object referent) {
    super(referent, ourQueue);
    synchronized(ourRefsList){
      ourRefsList.add(this);
    }

    if (ourTimer == null){
      ourTimer = new Timer(
        500,
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            processQueue();
          }
        }
      );
      ourTimer.setRepeats(true);
      ourTimer.start();
    }
  }

  /**
   * public for being accessible from the degenerator as timer stuff does not work.
   */
  public static void processQueue() {
    boolean haveClearedRefs = false;
    while(true){
      PatchedWeakReference ref = (PatchedWeakReference)ourQueue.poll();
      if (ref != null){
        haveClearedRefs = true;
      }
      else{
        break;
      }
    }
    if (!haveClearedRefs) return;

    synchronized(ourRefsList){
      ArrayList newList = new ArrayList();
      for(int i = 0; i < ourRefsList.size(); i++){
        PatchedWeakReference ref = (PatchedWeakReference)ourRefsList.get(i);
        if (ref.get() != null){
          newList.add(ref);
        }
      }



      if (LOG.isDebugEnabled()){
        LOG.info("old size:" + ourRefsList.size());
        LOG.info("new size:" + newList.size());
      }
      ourRefsList = newList;
    }
  }
}
