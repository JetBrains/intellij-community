package com.intellij.ide.util.treeView;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.Alarm;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.Iterator;
import java.util.LinkedList;

public class AbstractTreeUpdater {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.util.treeView.AbstractTreeUpdater");

  private int myDelay = 300;

  private final Alarm myAlarm = new Alarm();
  private LinkedList myNodesToUpdate = new LinkedList();
  private final AbstractTreeBuilder myTreeBuilder;
  private Runnable myRunAfterUpdate;
  private Runnable myRunBeforeUpdate;

  public AbstractTreeUpdater(AbstractTreeBuilder treeBuilder) {
    myTreeBuilder = treeBuilder;
  }

  /**
   * @param delay update delay in milliseconds.
   */
  public void setDelay(int delay) {
    myDelay = delay;
  }

  public void addSubtreeToUpdate(DefaultMutableTreeNode rootNode) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("addSubtreeToUpdate:" + rootNode);
    }

    for (Iterator iterator = myNodesToUpdate.iterator(); iterator.hasNext();) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) iterator.next();
      if (rootNode.isNodeAncestor(node)){
        return;
      }
      else if (node.isNodeAncestor(rootNode)){
        iterator.remove();
      }
    }
    myNodesToUpdate.add(rootNode);

    myAlarm.cancelAllRequests();
    myAlarm.addRequest(
      new Runnable() {
        public void run() {
          if (myTreeBuilder.isDisposed()) return;

          if (myTreeBuilder.getTreeStructure().hasSomethingToCommit()) {
            myAlarm.addRequest(this, myDelay);
            return;
          }

          myTreeBuilder.getTreeStructure().commit();
          //if (myAlarm.getActiveRequestCount() > 0) return; // do not update if commit caused some changes
          try {
            performUpdate();
          } catch(RuntimeException e) {
            LOG.error(myTreeBuilder.getClass().getName(), e);
          }
        }
      },
      myDelay,
      ModalityState.stateForComponent(myTreeBuilder.getTree())
    );
  }

  protected void updateSubtree(DefaultMutableTreeNode node) {
    myTreeBuilder.updateSubtree(node);
  }

  public void performUpdate() {
    if (myRunBeforeUpdate != null){
      myRunBeforeUpdate.run();
    }

    while(myNodesToUpdate.size() > 0){
      DefaultMutableTreeNode node = (DefaultMutableTreeNode) myNodesToUpdate.removeFirst();
      updateSubtree(node);
    }

    if (myRunAfterUpdate != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            synchronized (this) {
              if (myRunAfterUpdate != null) {
                myRunAfterUpdate.run();
                myRunAfterUpdate = null;
              }
            }
          }
        });
    }
  }

  public boolean addSubtreeToUpdateByElement(Object element) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("addSubtreeToUpdateByElement:" + element);
    }

    DefaultMutableTreeNode node = myTreeBuilder.getNodeForElement(element);
    if (node != null){
      addSubtreeToUpdate(node);
      return true;
    }
    else{
      return false;
    }
  }

  public boolean hasRequestsForUpdate() {
    return myAlarm.getActiveRequestCount() > 0;
  }

  public void cancelAllRequests(){
    myAlarm.cancelAllRequests();
  }

  public synchronized void runAfterUpdate(final Runnable runnable) {
    myRunAfterUpdate = runnable;
  }

  public synchronized void runBeforeUpdate(final Runnable runnable) {
    myRunBeforeUpdate = runnable;
  }
}
