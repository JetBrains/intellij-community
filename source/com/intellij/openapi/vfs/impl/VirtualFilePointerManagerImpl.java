package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandAdapter;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.ex.VirtualFileManagerListener;
import com.intellij.openapi.vfs.pointers.*;
import com.intellij.util.containers.HashMap;
import com.intellij.util.containers.WeakHashMap;
import com.intellij.util.containers.WeakList;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class VirtualFilePointerManagerImpl extends VirtualFilePointerManager implements ApplicationComponent{
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.VirtualFilePointerManagerImpl");
  private WeakHashMap<VirtualFilePointerImpl,Object> myPointers;
  private WeakList<VirtualFilePointerContainerImpl> myContainers;
  private MyVirtualFileListener myVirtualFileListener;
  private MyVirtualFileManagerListener myVirtualFileManagerListener;
  private MyCommandListener myCommandListener;

  private boolean myInsideRefresh = false;
  private boolean myInsideCommand = false;
  private boolean myChangesDetected = false;
  private VirtualFileManagerEx myVirtualFileManager;


  VirtualFilePointerManagerImpl(VirtualFileManagerEx virtualFileManagerEx,
                                CommandProcessor commandProcessor) {
    myPointers = new WeakHashMap<VirtualFilePointerImpl, Object>();
    myContainers = new WeakList<VirtualFilePointerContainerImpl>();
    myVirtualFileListener = new MyVirtualFileListener();
    myVirtualFileManagerListener = new MyVirtualFileManagerListener();
    myCommandListener = new MyCommandListener();

    virtualFileManagerEx.addVirtualFileListener(myVirtualFileListener);
    virtualFileManagerEx.addVirtualFileManagerListener(myVirtualFileManagerListener);
    commandProcessor.addCommandListener(myCommandListener);
    myVirtualFileManager = virtualFileManagerEx;
  }

  public void cleanupForNextTest() {
    myPointers = new WeakHashMap<VirtualFilePointerImpl, Object>();
    myContainers = new WeakList<VirtualFilePointerContainerImpl>();
  }

  public VirtualFilePointer create(String url, VirtualFilePointerListener listener) {
    VirtualFilePointerImpl pointer = new VirtualFilePointerImpl(url, listener, myVirtualFileManager);
    myPointers.put(pointer, null);
    return pointer;
  }

  public VirtualFilePointer create(VirtualFile file, VirtualFilePointerListener listener) {
    VirtualFilePointerImpl pointer = new VirtualFilePointerImpl(file, listener, myVirtualFileManager);
    myPointers.put(pointer, null);
    return pointer;
  }

  public VirtualFilePointer duplicate(VirtualFilePointer pointer, VirtualFilePointerListener listener) {
    VirtualFilePointerImpl newPointer = new VirtualFilePointerImpl((VirtualFilePointerImpl)pointer, listener, myVirtualFileManager);
    myPointers.put(newPointer, null);
    return newPointer;
  }

  public void kill(VirtualFilePointer pointer) {
    if (pointer == null) return;
    if (((VirtualFilePointerImpl)pointer).isDead()) return;
    myPointers.remove(pointer);
    ((VirtualFilePointerImpl)pointer).die();
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
    HashMap<VirtualFilePointerListener,ArrayList<VirtualFilePointer>> listenerToArrayOfPointers = buildIterationMap(listenerNotifier);
    iterateMap(listenerToArrayOfPointers, listenerNotifier);
    return listenerToArrayOfPointers;
  }

  private void iterateMap(HashMap<VirtualFilePointerListener, ArrayList<VirtualFilePointer>> listenerToArrayOfPointers, PointerProcessor listenerCollector) {
    Iterator<VirtualFilePointerListener> keys = listenerToArrayOfPointers.keySet().iterator();
    while (keys.hasNext()) {
      VirtualFilePointerListener listener = keys.next();
      ArrayList<VirtualFilePointer> list = listenerToArrayOfPointers.get(listener);
      final VirtualFilePointer[] pointers = (VirtualFilePointer[])list.toArray(new VirtualFilePointer[list.size()]);
      listenerCollector.notifyListeners(listener, pointers);
    }
  }

  private HashMap<VirtualFilePointerListener, ArrayList<VirtualFilePointer>> buildIterationMap(PointerProcessor listenerNotifier) {
    Iterator<VirtualFilePointerImpl> iterator = myPointers.keySet().iterator();
    HashMap<VirtualFilePointerListener, ArrayList<VirtualFilePointer>> listenerToArrayOfPointers =
      new HashMap<VirtualFilePointerListener,ArrayList<VirtualFilePointer>>();
    while (iterator.hasNext()) {
      VirtualFilePointerImpl pointer = iterator.next();
      if (pointer != null) {
        if (listenerNotifier.processPointer(pointer)) {
          VirtualFilePointerListener listener = pointer.getListener();
          if (listener != null) {
            ArrayList<VirtualFilePointer> list = listenerToArrayOfPointers.get(listener);
            if (list == null) {
              list = new ArrayList<VirtualFilePointer>();
              listenerToArrayOfPointers.put(listener, list);
            }
            list.add(pointer);
          }
        }
      }
    }

    return listenerToArrayOfPointers;
  }

  private void validate() {
    cleanContainerCaches();
    iteratePointers(PointerValidityChangeDetector.INSTANCE);
    iteratePointers(PointerValidator.INSTANCE);
  }

  private void cleanContainerCaches() {
    for (Iterator<VirtualFilePointerContainerImpl> iterator = myContainers.iterator(); iterator.hasNext();) {
      VirtualFilePointerContainerImpl container = iterator.next();
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
      for (Iterator<VirtualFilePointerImpl> iterator = invalidatedPointers.iterator(); iterator.hasNext();) {
        VirtualFilePointerImpl pointer = iterator.next();
        pointer.invalidateByDeletion();
      }
      iterateMap(fileDeletedNotificationMap, PointerValidator.INSTANCE);
    }

    private void handleEvent() {
      if (!myInsideRefresh && !myInsideCommand) {
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
      myInsideRefresh = true;
    }

    public void afterRefreshFinish(boolean asynchonous) {
      myInsideRefresh = false;
      if (myChangesDetected && !myInsideCommand) {
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
      if (myChangesDetected && !myInsideRefresh) {
        myChangesDetected = false;
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            validate();
          }
        });
      }
    }
  }
}
