/*
 * OpsBlockingQueue.java
 *
 * Created on May 3, 2001, 3:11 PM
 */
package opsx.util;

import opsx.log.OpsLogCategory;
import opsx.server.email.OpsEmailGenerator;

/** @author dchan */
public class OpsBlockingQueue extends OpsNonBlockingQueue {
  //***********************************       PROTECTED/PACKAGE FIELDS        **************************************
  protected final    Object putLock;
  protected final    Object takeLock;
  protected volatile int    waitCount;
  //**************************************        PRIVATE FIELDS          *****************************************
  private static final OpsLogCategory logCat = (OpsLogCategory)OpsLogCategory.getInstance(OpsEmailGenerator.class);
//**************************************        CONSTRUCTORS              *************************************

  /** Creates new OpsBlockingQueue */
  public OpsBlockingQueue() {
    takeLock = new Object();
    putLock = new Object();
    waitCount = 0;
  }

  //**************************************        PUBLIC METHODS              *************************************
  public void put(Object obj) {
    synchronized (putLock) {
      insert(obj);
      if (waitCount > 0) {
        putLock.notifyAll();
      }
    }
  }

  public Object take() {
    Object obj = takeNext();
    if (obj != null) {
      return obj;
    }
    else {
      synchronized (putLock) {
        waitCount++;
        while (true) {
          obj = takeNext();
          if (obj != null) {
            waitCount--;
            return obj;
          }
          try {
            putLock.wait();
          }
          catch (InterruptedException e) {
            logCat.error("Blocking Queue threw interrupt", e);
            return null;
          }
        }
      }
    }
  }

  //*********************************     PACKAGE/PROTECTED METHODS              ********************************
  protected Object takeNext() {
    synchronized (takeLock) {
      return super.takeNext();
    }
  }
}