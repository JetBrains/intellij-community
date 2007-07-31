package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.ex.dummy.DummyFileSystem;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.openapi.vfs.pointers.*;
import com.intellij.util.containers.WeakList;
import com.intellij.util.containers.WeakValueHashMap;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.text.CaseInsensitiveStringHashingStrategy;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import gnu.trove.TObjectHashingStrategy;

import java.util.ArrayList;
import java.util.List;

public class VirtualFilePointerManagerImpl extends VirtualFilePointerManager implements ApplicationComponent{
  private THashMap<VirtualFilePointerListener, THashSet<VirtualFilePointer>> myListenerToPointersMap;

  private WeakValueHashMap<String, VirtualFilePointer> myUrlToPointerMap;

  private WeakList<VirtualFilePointerContainerImpl> myContainers;

  private VirtualFileManagerEx myVirtualFileManager;


  VirtualFilePointerManagerImpl(VirtualFileManagerEx virtualFileManagerEx,
                                CommandProcessor commandProcessor,
                                MessageBus bus) {
    initContainers();

    bus.connect().subscribe(VirtualFileManager.VFS_CHANGES, new VFSEventsProcessor());

    myVirtualFileManager = virtualFileManagerEx;
  }

  private class EventDescriptor {
    private VirtualFilePointerListener myListener;
    private List<VirtualFilePointer> myPointers;

    private EventDescriptor(VirtualFilePointerListener listener, List<VirtualFilePointer> pointers) {
      myListener = listener;
      myPointers = new ArrayList<VirtualFilePointer>(myListenerToPointersMap.get(listener));
      myPointers.retainAll(pointers);
    }

    public void fireBefore() {
      if (!myPointers.isEmpty()) {
        myListener.beforeValidityChanged(myPointers.toArray(new VirtualFilePointer[myPointers.size()]));
      }
    }

    public void fireAfter() {
      if (!myPointers.isEmpty()) {
        myListener.validityChanged(myPointers.toArray(new VirtualFilePointer[myPointers.size()]));
      }
    }
  }

  private List<VirtualFilePointer> getPointersUnder(String url) {
    List<VirtualFilePointer> pointers = new ArrayList<VirtualFilePointer>();
    for (String pointerUrl : myUrlToPointerMap.keySet()) {
      if (startsWith(url, pointerUrl)) {
        VirtualFilePointer pointer = myUrlToPointerMap.get(pointerUrl);
        if (pointer != null) {
          pointers.add(pointer);
        }
      }
    }
    return pointers;
  }

  private static boolean startsWith(final String url, final String pointerUrl) {
    String urlSuffix = stripSuffix(url);
    String pointerPrefix = stripToJarPrefix(pointerUrl);
    if (urlSuffix.length() > 0) {
      return Comparing.equal(stripToJarPrefix(url), pointerPrefix, SystemInfo.isFileSystemCaseSensitive) &&
             StringUtil.startsWith(urlSuffix, stripSuffix(pointerUrl));
    }

    return FileUtil.startsWith(pointerPrefix, stripToJarPrefix(url));
  }

  private static String stripToJarPrefix(String url) {
    int separatorIndex = url.indexOf(JarFileSystem.JAR_SEPARATOR);
    if (separatorIndex < 0) return url;
    return url.substring(0, separatorIndex);
  }

  private static String stripSuffix(String url) {
    int separatorIndex = url.indexOf(JarFileSystem.JAR_SEPARATOR);
    if (separatorIndex < 0) return "";
    return url.substring(separatorIndex + JarFileSystem.JAR_SEPARATOR.length());
  }

  public void cleanupForNextTest() {
    initContainers();
  }

  private void initContainers() {
    myContainers = new WeakList<VirtualFilePointerContainerImpl>();
    myListenerToPointersMap = new THashMap<VirtualFilePointerListener, THashSet<VirtualFilePointer>>();
    myUrlToPointerMap = new WeakValueHashMap<String, VirtualFilePointer>(SystemInfo.isFileSystemCaseSensitive
                                                                         ? (TObjectHashingStrategy<String>)TObjectHashingStrategy.CANONICAL
                                                                         : CaseInsensitiveStringHashingStrategy.INSTANCE);
  }

  private synchronized VirtualFilePointer doAdd(String url, VirtualFile file, final VirtualFilePointerListener listener) {
    if (file != null && file.getFileSystem() instanceof DummyFileSystem) {
      return new VirtualFilePointerImpl(file, myVirtualFileManager);
    }

    String path = VfsUtil.urlToPath(url);

    VirtualFilePointer pointer = myUrlToPointerMap.get(path);
    final boolean notexists = pointer == null;

    if (notexists) {
      pointer =
        file == null ? new VirtualFilePointerImpl(url, myVirtualFileManager) : new VirtualFilePointerImpl(file, myVirtualFileManager);
      myUrlToPointerMap.put(path, pointer);
    }

    if (listener != null) {
      THashSet<VirtualFilePointer> pointerSet = myListenerToPointersMap.get(listener);
      if (pointerSet == null) {
        pointerSet = new THashSet<VirtualFilePointer>();
        myListenerToPointersMap.put(listener, pointerSet);
      }
      pointerSet.add(pointer);
    }

    return pointer;
  }

  public VirtualFilePointer create(String url, VirtualFilePointerListener listener) {
    return doAdd(url, null, listener);
  }

  public VirtualFilePointer create(VirtualFile file, VirtualFilePointerListener listener) {
    return doAdd(file.getUrl(), file, listener);
  }

  public synchronized VirtualFilePointer duplicate(VirtualFilePointer pointer, VirtualFilePointerListener listener) {
    if (listener != null) {
      THashSet<VirtualFilePointer> virtualFilePointers = myListenerToPointersMap.get(listener);

      if (virtualFilePointers == null) {
        virtualFilePointers = new THashSet<VirtualFilePointer>();
        myListenerToPointersMap.put(listener, virtualFilePointers);
      }
      virtualFilePointers.add(pointer);
    }

    return pointer;
  }

  public synchronized void kill(VirtualFilePointer pointer, final VirtualFilePointerListener listener) {
    if (listener != null) {
      final THashSet<VirtualFilePointer> pointerSet = myListenerToPointersMap.get(listener);
      if (pointerSet != null) {
        pointerSet.remove(pointer);
        if (pointerSet.size() == 0) myListenerToPointersMap.remove(listener);
      }
    }
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public String getComponentName() {
    return "SmartVirtualPointerManager";
  }

  private void cleanContainerCaches() {
    for (VirtualFilePointerContainerImpl container : myContainers) {
      container.dropCaches();
    }
  }

  public VirtualFilePointerContainer createContainer() {
    final VirtualFilePointerContainerImpl virtualFilePointerContainer = new VirtualFilePointerContainerImpl();
    myContainers.add(virtualFilePointerContainer);
    return virtualFilePointerContainer;
  }

  public VirtualFilePointerContainer createContainer(VirtualFilePointerFactory factory) {
    final VirtualFilePointerContainerImpl virtualFilePointerContainer = new VirtualFilePointerContainerImpl(factory);
    myContainers.add(virtualFilePointerContainer);
    return virtualFilePointerContainer;
  }


  private class VFSEventsProcessor implements BulkFileListener {
    private List<EventDescriptor> myEvents = null;
    private List<String> myUrlsToUpdate = null;
    private List<VirtualFilePointer> myPointersToUdate = null;

    public void before(final List<? extends VFileEvent> events) {
      List<VirtualFilePointer> toFireEvents = new ArrayList<VirtualFilePointer>();
      List<String> toUpdateUrl = new ArrayList<String>();

      for (VFileEvent event : events) {
        if (event instanceof VFileDeleteEvent) {
          final VFileDeleteEvent deleteEvent = (VFileDeleteEvent)event;
          String url = deleteEvent.getFile().getPath();
          toFireEvents.addAll(getPointersUnder(url));
        }
        else if (event instanceof VFileCreateEvent) {
          final VFileCreateEvent createEvent = (VFileCreateEvent)event;
          String url = createEvent.getPath();
          toFireEvents.addAll(getPointersUnder(url));
        }
        else if (event instanceof VFileCopyEvent) {
          final VFileCopyEvent copyEvent = (VFileCopyEvent)event;
          String url = copyEvent.getNewParent().getPath() + "/" + copyEvent.getFile().getName();
          toFireEvents.addAll(getPointersUnder(url));
        }
        else if (event instanceof VFileMoveEvent) {
          final VFileMoveEvent moveEvent = (VFileMoveEvent)event;
          List<VirtualFilePointer> pointers = getPointersUnder(moveEvent.getFile().getPath());
          for (VirtualFilePointer pointer : pointers) {
            VirtualFile file = pointer.getFile();
            if (file != null) {
              toUpdateUrl.add(file.getPath());
            }
          }
        }
        else if (event instanceof VFilePropertyChangeEvent) {
          final VFilePropertyChangeEvent change = (VFilePropertyChangeEvent)event;
          if (VirtualFile.PROP_NAME.equals(change.getPropertyName())) {
            List<VirtualFilePointer> pointers = getPointersUnder(change.getFile().getPath());
            for (VirtualFilePointer pointer : pointers) {
              VirtualFile file = pointer.getFile();
              if (file != null) {
                toUpdateUrl.add(file.getPath());
              }
            }
          }
        }
      }

      myEvents = new ArrayList<EventDescriptor>();
      for (VirtualFilePointerListener listener : myListenerToPointersMap.keySet()) {
        EventDescriptor event = new EventDescriptor(listener, toFireEvents);
        myEvents.add(event);
        event.fireBefore();
      }

      myPointersToUdate = toFireEvents;
      myUrlsToUpdate = toUpdateUrl;
    }

    public void after(final List<? extends VFileEvent> events) {
      for (String url : myUrlsToUpdate) {
        VirtualFilePointer pointer = myUrlToPointerMap.remove(url);
        if (pointer != null) {
          myUrlToPointerMap.put(VfsUtil.urlToPath(pointer.getUrl()), pointer);
        }
      }

      for (VirtualFilePointer pointer : myPointersToUdate) {
        ((VirtualFilePointerImpl)pointer).update();
      }

      for (EventDescriptor event : myEvents) {
        event.fireAfter();
      }

      myUrlsToUpdate = null;
      myEvents = null;
      myPointersToUdate = null;
    }
  }
}
