package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.pointers.*;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.WeakList;
import gnu.trove.THashMap;
import gnu.trove.THashSet;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.*;

public class VirtualFilePointerManagerImpl extends VirtualFilePointerManager implements ApplicationComponent{
  private WeakHashMap<VirtualFilePointerListener, THashSet<VirtualFilePointer>> myListenerToPointersMap;

  private THashMap<Object, MyWeakReference<VirtualFilePointer>> myUrlToPointerMap;
  private ReferenceQueue<VirtualFilePointer> myReferenceQueue;
  private WeakList<VirtualFilePointerContainerImpl> myContainers;

  private MyVirtualFileListener myVirtualFileListener;
  private MyVirtualFileManagerListener myVirtualFileManagerListener;
  private MyCommandListener myCommandListener;

  private int myInsideRefresh = 0;
  private boolean myInsideCommand = false;
  private boolean myChangesDetected = false;
  private VirtualFileManagerEx myVirtualFileManager;


  VirtualFilePointerManagerImpl(VirtualFileManagerEx virtualFileManagerEx,
                                CommandProcessor commandProcessor) {
    cleanupForNextTest();
    myVirtualFileListener = new MyVirtualFileListener();
    myVirtualFileManagerListener = new MyVirtualFileManagerListener();
    myCommandListener = new MyCommandListener();

    virtualFileManagerEx.addVirtualFileListener(myVirtualFileListener);
    virtualFileManagerEx.addVirtualFileManagerListener(myVirtualFileManagerListener);
    commandProcessor.addCommandListener(myCommandListener);
    myVirtualFileManager = virtualFileManagerEx;
  }

  public void cleanupForNextTest() {
    myReferenceQueue = new ReferenceQueue<VirtualFilePointer>();
    myContainers = new WeakList<VirtualFilePointerContainerImpl>();
    myListenerToPointersMap = new WeakHashMap<VirtualFilePointerListener, THashSet<VirtualFilePointer>>();
    myUrlToPointerMap = new THashMap<Object, MyWeakReference<VirtualFilePointer>>();
  }

  private synchronized VirtualFilePointer doAdd(final Object url, final VirtualFilePointerListener listener) {
    flushQueue();
    MyWeakReference<VirtualFilePointer> weakReference = myUrlToPointerMap.get(url);
    VirtualFilePointer existingPointer = weakReference != null ? weakReference.get():null;
    final boolean notexists = existingPointer == null;

    if (notexists) {
      existingPointer = url instanceof String ?
                        new VirtualFilePointerImpl((String)url, myVirtualFileManager):
                        new VirtualFilePointerImpl((VirtualFile)url, myVirtualFileManager);
      weakReference = new MyWeakReference<VirtualFilePointer>(existingPointer, myReferenceQueue, url);
      myUrlToPointerMap.put(url, weakReference);
    }

    if (listener != null) {
      THashSet<VirtualFilePointer> pointerSet = myListenerToPointersMap.get(listener);
      if (pointerSet == null) {
        pointerSet = new THashSet<VirtualFilePointer>();
        myListenerToPointersMap.put(listener, pointerSet);
      }
      pointerSet.add(existingPointer);
    }

    return existingPointer;
  }

  private void flushQueue() {
    MyWeakReference<VirtualFilePointer> reference;
    while ((reference = (MyWeakReference<VirtualFilePointer>)myReferenceQueue.poll()) != null) {
      myUrlToPointerMap.remove(reference.myTrackingKey);
    }
  }
  
  public VirtualFilePointer create(String url, VirtualFilePointerListener listener) {
    return doAdd(url, listener);
  }

  public VirtualFilePointer create(VirtualFile file, VirtualFilePointerListener listener) {
    return doAdd(file, listener);
  }

  public VirtualFilePointer duplicate(VirtualFilePointer pointer, VirtualFilePointerListener listener) {
    synchronized(this) {
      if (listener != null) {
        THashSet<VirtualFilePointer> virtualFilePointers = myListenerToPointersMap.get(listener);

        if (virtualFilePointers == null) {
          virtualFilePointers = new THashSet<VirtualFilePointer>();
          myListenerToPointersMap.put(listener, virtualFilePointers);
        }
        virtualFilePointers.add(pointer);
      }
      flushQueue();
    }
    return pointer;
  }

  public void kill(VirtualFilePointer pointer, final VirtualFilePointerListener listener) {
    if (pointer == null) return;
    synchronized(this) {
      if (listener != null) {
        final THashSet<VirtualFilePointer> pointerSet = myListenerToPointersMap.get(listener);
        if (pointerSet != null) {
          pointerSet.remove(pointer);
          if (pointerSet.size() == 0) myListenerToPointersMap.remove(listener);
        }
      }
      flushQueue();
    }
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public String getComponentName() {
    return "SmartVirtualPointerManager";
  }

  private interface PointerProcessor {
    void notifyListeners(VirtualFilePointerListener listener, VirtualFilePointer[] pointers);
    /**
     * @return true if event should be fired for this pointer.
     */
    boolean processPointer(VirtualFilePointerImpl pointer);
  }

  private HashMap<VirtualFilePointerListener,ArrayList<VirtualFilePointer>> iteratePointers(PointerProcessor listenerNotifier) {
    final HashMap<VirtualFilePointerListener, ArrayList<VirtualFilePointer>> listenerToArrayOfPointers =
      new HashMap<VirtualFilePointerListener,ArrayList<VirtualFilePointer>>();

    synchronized(this) {
      Set<VirtualFilePointer> visitedPointers = new HashSet<VirtualFilePointer>(myUrlToPointerMap.size());

      for(Map.Entry<VirtualFilePointerListener,THashSet<VirtualFilePointer>> entry:myListenerToPointersMap.entrySet()) {
        ArrayList<VirtualFilePointer> list = null;
  
        for(VirtualFilePointer _pointer:entry.getValue()) {
          VirtualFilePointerImpl pointer = (VirtualFilePointerImpl)_pointer;

          if (listenerNotifier.processPointer(pointer)) {
            if (list == null) {
              list = new ArrayList<VirtualFilePointer>();
              listenerToArrayOfPointers.put(entry.getKey(), list);
            }
            visitedPointers.add(pointer);
            list.add(pointer);
          }
        }
      }

      for(WeakReference<VirtualFilePointer> pointer:myUrlToPointerMap.values()) {
        final VirtualFilePointer filePointer = pointer.get();
        if (filePointer != null && !visitedPointers.contains(filePointer)) {
          visitedPointers.add(filePointer);
          listenerNotifier.processPointer((VirtualFilePointerImpl)filePointer);
        }
      }
    }

    iterateMap(listenerToArrayOfPointers, listenerNotifier);

    return listenerToArrayOfPointers;
  }

  private static void iterateMap(HashMap<VirtualFilePointerListener, ArrayList<VirtualFilePointer>> listenerToArrayOfPointers, PointerProcessor listenerCollector) {
    for (VirtualFilePointerListener listener : listenerToArrayOfPointers.keySet()) {
      ArrayList<VirtualFilePointer> list = listenerToArrayOfPointers.get(listener);
      final VirtualFilePointer[] pointers = list.toArray(new VirtualFilePointer[list.size()]);
      listenerCollector.notifyListeners(listener, pointers);
    }
  }

  private void validate() {
    cleanContainerCaches();

    iteratePointers(PointerValidityChangeDetector.INSTANCE);
    iteratePointers(PointerValidator.INSTANCE);
  }

  private void cleanContainerCaches() {
    for (VirtualFilePointerContainerImpl container : myContainers) {
      container.dropCaches();
    }
  }

  private static class PointerValidator implements PointerProcessor {
    private final static PointerValidator INSTANCE = new PointerValidator();
    public void notifyListeners(VirtualFilePointerListener listener, final VirtualFilePointer[] pointers) {
      listener.validityChanged(pointers);
    }

    public boolean processPointer(VirtualFilePointerImpl pointer) {
      boolean wasValid = pointer.wasRecentlyValid();
      pointer.update();
      return pointer.wasRecentlyValid() != wasValid;
    }
  }

  private static class PointerValidityChangeDetector implements PointerProcessor {
    private static final PointerValidityChangeDetector INSTANCE = new PointerValidityChangeDetector();
    public boolean processPointer(VirtualFilePointerImpl pointer) {
      return pointer.willValidityChange();
    }

    public void notifyListeners(VirtualFilePointerListener listener, VirtualFilePointer[] pointers) {
      listener.beforeValidityChanged(pointers);
    }
  }

  private class MyVirtualFileListener extends VirtualFileAdapter {

    public void propertyChanged(VirtualFilePropertyEvent event) {
      if (VirtualFile.PROP_NAME.equals(event.getPropertyName())) {
        handleEvent();
      }
    }

    public void fileCreated(VirtualFileEvent event) {
      handleEvent();
    }

    public void fileMoved(VirtualFileMoveEvent event) {
      handleEvent();
    }

    public void beforeFileDeletion(final VirtualFileEvent event) {
      cleanContainerCaches();
      final List<VirtualFilePointerImpl> invalidatedPointers = new ArrayList<VirtualFilePointerImpl>();
      PointerProcessor listenerNotifier = new PointerProcessor() {
        public boolean processPointer(VirtualFilePointerImpl pointer) {
          if (invalidatedPointers.contains(pointer)) return true; // See http://www.jetbrains.net/jira/browse/IDEADEV-14363

          final boolean invalidated = isInvalidatedByDeletion(pointer, event);
          if (invalidated) {
            invalidatedPointers.add(pointer);
          }
          return invalidated;
        }

        public void notifyListeners(VirtualFilePointerListener listener, VirtualFilePointer[] pointers) {
          listener.beforeValidityChanged(pointers);
        }
      };


      HashMap<VirtualFilePointerListener,ArrayList<VirtualFilePointer>> fileDeletedNotificationMap = iteratePointers(listenerNotifier);
      for (VirtualFilePointerImpl pointer : invalidatedPointers) {
        pointer.invalidateByDeletion();
      }
      iterateMap(fileDeletedNotificationMap, PointerValidator.INSTANCE);
    }

    private void handleEvent() {
      if (myInsideRefresh == 0 && !myInsideCommand) {
        validate();
      }
      else {
        myChangesDetected = true;
      }
    }
  }

  private static boolean isInvalidatedByDeletion(VirtualFilePointerImpl pointer, final VirtualFileEvent event) {
    return pointer.isValid() && VfsUtil.isAncestor(event.getFile(), pointer.getFile(), false);
  }

  public VirtualFilePointerContainer createContainer() {
    final VirtualFilePointerContainerImpl virtualFilePointerContainer = new VirtualFilePointerContainerImpl();
    myContainers.add(virtualFilePointerContainer);
    return virtualFilePointerContainer;
  }

  public VirtualFilePointerContainer createContainer(VirtualFilePointerListener listener) {
    final VirtualFilePointerContainerImpl virtualFilePointerContainer = new VirtualFilePointerContainerImpl(listener);
    myContainers.add(virtualFilePointerContainer);
    return virtualFilePointerContainer;
  }

  public VirtualFilePointerContainer createContainer(VirtualFilePointerFactory factory) {
    final VirtualFilePointerContainerImpl virtualFilePointerContainer = new VirtualFilePointerContainerImpl(factory);
    myContainers.add(virtualFilePointerContainer);
    return virtualFilePointerContainer;
  }


  private class MyVirtualFileManagerListener implements VirtualFileManagerListener {
    public void beforeRefreshStart(boolean asynchonous) {
      myInsideRefresh++;
    }

    public void afterRefreshFinish(boolean asynchonous) {
      myInsideRefresh--;
      if (myInsideRefresh == 0 && myChangesDetected  && !myInsideCommand) {
        myChangesDetected = false;
        validate();
      }
    }
  }

  private class MyCommandListener extends CommandAdapter {
    public void commandStarted(CommandEvent event){
      myInsideCommand = true;
    }

    public void commandFinished(CommandEvent event){
      myInsideCommand = false;
      if (myChangesDetected && myInsideRefresh == 0) {
        myChangesDetected = false;
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            validate();
          }
        });
      }
    }
  }

  private static class MyWeakReference<T> extends WeakReference<T> {
    private final Object myTrackingKey;

    MyWeakReference(T referent, ReferenceQueue<T> referenceQueue, Object _trackingKey) {
      super(referent, referenceQueue);
      myTrackingKey = _trackingKey;
    }
  }
}
