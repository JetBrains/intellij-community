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
package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class PatchedWeakReference<T> extends WeakReference<T>{
  private static final Logger LOG = Logger.getInstance("#com.intellij.util.PatchedWeakReference");

  private static ArrayList<PatchedWeakReference<?>> ourRefsList = new ArrayList<PatchedWeakReference<?>>();
  private static ReferenceQueue ourQueue = new ReferenceQueue();
  private static Timer ourTimer = null;


  static {
    ourTimer = new Timer();
    ourTimer.schedule(new TimerTask() {
      public void run() {
        processQueue();
      }
    }, 500, 500);
  }

  public PatchedWeakReference(T referent) {
    super(referent, ourQueue);
    synchronized(ourRefsList){
      ourRefsList.add(this);
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
      ArrayList<PatchedWeakReference<?>> newList = new ArrayList<PatchedWeakReference<?>>();
      for(int i = 0; i < ourRefsList.size(); i++){
        PatchedWeakReference<?> ref = ourRefsList.get(i);
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
