package com.intellij.openapi.vfs.impl;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.FileContentProvider;
import com.intellij.openapi.vfs.ex.ProvidedContent;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.openapi.vfs.newvfs.*;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.util.EventDispatcher;
import com.intellij.util.PendingEventDispatcher;
import com.intellij.util.containers.HashMap;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.io.File;
import java.io.IOException;

public class VirtualFileManagerImpl extends VirtualFileManagerEx implements ApplicationComponent {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.VirtualFileManagerImpl");

  private ArrayList<VirtualFileSystem> myFileSystems = null;
  private HashMap<String, VirtualFileSystem> myProtocolToSystemMap = null;

  private PendingEventDispatcher<VirtualFileListener> myVirtualFileListenerMulticaster =
    PendingEventDispatcher.create(VirtualFileListener.class);
  private List<VirtualFileManagerListener> myVirtualFileManagerListeners = new CopyOnWriteArrayList<VirtualFileManagerListener>();
  private EventDispatcher<ModificationAttemptListener> myModificationAttemptListenerMulticaster =
    EventDispatcher.create(ModificationAttemptListener.class);

  private ArrayList<FileContentProvider> myContentProviders = new ArrayList<FileContentProvider>();
  private PendingEventDispatcher<VirtualFileListener> myContentProvidersDispatcher =
    PendingEventDispatcher.create(VirtualFileListener.class);
  @NonNls private static final String USER_HOME = "user.home";

  public VirtualFileManagerImpl(VirtualFileSystem[] fileSystems, MessageBus bus) {
    myFileSystems = new ArrayList<VirtualFileSystem>();
    myProtocolToSystemMap = new HashMap<String, VirtualFileSystem>();
    for (VirtualFileSystem fileSystem : fileSystems) {
      registerFileSystem(fileSystem);
    }

    if (LOG.isDebugEnabled()) {
      addVirtualFileListener(new LoggingListener());
    }
    addVirtualFileListener(myContentProvidersDispatcher.getMulticaster());

    bus.connect().subscribe(VFS_CHANGES, new BulkFileListener() {
      public void before(final List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          fireBefore(event);
        }
      }

      public void after(final List<? extends VFileEvent> events) {
        for (VFileEvent event : events) {
          fireAfter(event);
        }
      }
    });
  }

  private void fireAfter(final VFileEvent event) {
    if (event instanceof VFileContentChangeEvent) {
      final VFileContentChangeEvent ce = (VFileContentChangeEvent)event;
      final VirtualFile file = ce.getFile();
      myVirtualFileListenerMulticaster.getMulticaster()
        .contentsChanged(new VirtualFileEvent(event.getRequestor(), file, file.getParent(), ce.getOldModificationStamp(), ce.getModificationStamp()));
    }
    else if (event instanceof VFileCopyEvent) {
      final VFileCopyEvent ce = (VFileCopyEvent)event;
      myVirtualFileListenerMulticaster.getMulticaster()
        .fileCopied(new VirtualFileCopyEvent(event.getRequestor(), ce.getFile(), ce.getNewParent().findChild(ce.getNewChildName())));
    }
    else if (event instanceof VFileCreateEvent) {
      final VFileCreateEvent ce = (VFileCreateEvent)event;
      final VirtualFile newChild = ce.getParent().findChild(ce.getChildName());
      if (newChild != null) {
        myVirtualFileListenerMulticaster.getMulticaster().fileCreated(
        new VirtualFileEvent(event.getRequestor(), newChild, ce.getChildName(), ce.getParent()));
      }
    }
    else if (event instanceof VFileDeleteEvent) {
      final VFileDeleteEvent de = (VFileDeleteEvent)event;
      myVirtualFileListenerMulticaster.getMulticaster()
        .fileDeleted(new VirtualFileEvent(event.getRequestor(), de.getFile(), de.getFile().getParent(), 0, 0));
    }
    else if (event instanceof VFileMoveEvent) {
      final VFileMoveEvent me = (VFileMoveEvent)event;
      myVirtualFileListenerMulticaster.getMulticaster().fileMoved(new VirtualFileMoveEvent(event.getRequestor(), me.getFile(), me.getOldParent(), me.getNewParent()));
    }
    else if (event instanceof VFilePropertyChangeEvent) {
      final VFilePropertyChangeEvent pce = (VFilePropertyChangeEvent)event;
      myVirtualFileListenerMulticaster.getMulticaster().propertyChanged(
        new VirtualFilePropertyEvent(event.getRequestor(), pce.getFile(), pce.getPropertyName(), pce.getOldValue(), pce.getNewValue()));
    }
  }

  private void fireBefore(final VFileEvent event) {
    if (event instanceof VFileContentChangeEvent) {
      final VFileContentChangeEvent ce = (VFileContentChangeEvent)event;
      final VirtualFile file = ce.getFile();
      myVirtualFileListenerMulticaster.getMulticaster()
        .beforeContentsChange(new VirtualFileEvent(event.getRequestor(), file, file.getParent(), ce.getOldModificationStamp(), ce.getModificationStamp()));
    }
    else if (event instanceof VFileDeleteEvent) {
      final VFileDeleteEvent de = (VFileDeleteEvent)event;
      myVirtualFileListenerMulticaster.getMulticaster()
        .beforeFileDeletion(new VirtualFileEvent(event.getRequestor(), de.getFile(), de.getFile().getParent(), 0, 0));
    }
    else if (event instanceof VFileMoveEvent) {
      final VFileMoveEvent me = (VFileMoveEvent)event;
      myVirtualFileListenerMulticaster.getMulticaster().beforeFileMovement(new VirtualFileMoveEvent(event.getRequestor(), me.getFile(), me.getOldParent(), me.getNewParent()));
    }
    else if (event instanceof VFilePropertyChangeEvent) {
      final VFilePropertyChangeEvent pce = (VFilePropertyChangeEvent)event;
      myVirtualFileListenerMulticaster.getMulticaster().beforePropertyChange(
        new VirtualFilePropertyEvent(event.getRequestor(), pce.getFile(), pce.getPropertyName(), pce.getOldValue(), pce.getNewValue()));
    }
  }

  @NotNull
  public String getComponentName() {
    return "VirtualFileManager";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public void registerFileSystem(VirtualFileSystem fileSystem) {
    myFileSystems.add(fileSystem);
    if (!(fileSystem instanceof NewVirtualFileSystem)) {
      fileSystem.addVirtualFileListener(myVirtualFileListenerMulticaster.getMulticaster());
    }
    myProtocolToSystemMap.put(fileSystem.getProtocol(), fileSystem);
  }

  public void unregisterFileSystem(VirtualFileSystem fileSystem) {
    myFileSystems.remove(fileSystem);
    fileSystem.removeVirtualFileListener(myVirtualFileListenerMulticaster.getMulticaster());
    myProtocolToSystemMap.remove(fileSystem.getProtocol());
  }

  public VirtualFileSystem[] getFileSystems() {
    return myFileSystems.toArray(new VirtualFileSystem[myFileSystems.size()]);
  }

  public VirtualFileSystem getFileSystem(String protocol) {
    return myProtocolToSystemMap.get(protocol);
  }

  public void refresh(boolean asynchronous) {
    refresh(asynchronous, null);
  }

  public void refreshWithoutFileWatcher(final boolean asynchronous) {
    if (!asynchronous) {
      ApplicationManager.getApplication().assertIsDispatchThread();
    }

    for (VirtualFileSystem fileSystem : myFileSystems) {
      if (fileSystem instanceof NewVirtualFileSystem) {
        ((NewVirtualFileSystem)fileSystem).refreshWithoutFileWatcher(asynchronous);
      }
      else {
        fileSystem.refresh(asynchronous);
      }
    }
  }

  public void refresh(boolean asynchronous, final Runnable postAction) {
    final ModalityState modalityState = calcModalityStateForRefreshEventsPosting(asynchronous);

    beforeRefreshStart(asynchronous, modalityState, postAction);

    try {
      if (!asynchronous) {
        ApplicationManager.getApplication().assertIsDispatchThread();
      }

      //RefreshQueue.getInstance().refresh(asynchronous, true, postAction, ManagingFS.getInstance().getRoots()); // TODO: Get an idea how to deliver chnages from local FS to jar fs before they go refresh

      final VirtualFile[] managedRoots = ManagingFS.getInstance().getRoots();
      for (int i = 0; i < managedRoots.length; i++) {
        VirtualFile root = managedRoots[i];
        boolean last = i + 1 == managedRoots.length;
        RefreshQueue.getInstance().refresh(asynchronous, true, last ? postAction : null, root);
      }

      for (VirtualFileSystem fileSystem : myFileSystems) {
        if (!(fileSystem instanceof NewVirtualFileSystem)) {
          fileSystem.refresh(asynchronous);
        }
      }
    }
    finally {
      afterRefreshFinish(asynchronous, modalityState);
    }
  }

  public VirtualFile findFileByUrl(@NotNull String url) {
    String protocol = extractProtocol(url);
    if (protocol == null) return null;
    VirtualFileSystem fileSystem = myProtocolToSystemMap.get(protocol);
    if (fileSystem == null) return null;
    return fileSystem.findFileByPath(extractPath(url));
  }

  public VirtualFile refreshAndFindFileByUrl(@NotNull String url) {
    String protocol = extractProtocol(url);
    if (protocol == null) return null;
    VirtualFileSystem fileSystem = myProtocolToSystemMap.get(protocol);
    if (fileSystem == null) return null;
    String path = extractPath(url);
    return fileSystem.refreshAndFindFileByPath(path);
  }

  public void addVirtualFileListener(@NotNull VirtualFileListener listener) {
    myVirtualFileListenerMulticaster.addListener(listener);
  }

  public void addVirtualFileListener(@NotNull VirtualFileListener listener, Disposable parentDisposable) {
    myVirtualFileListenerMulticaster.addListener(listener, parentDisposable);
  }

  public void removeVirtualFileListener(@NotNull VirtualFileListener listener) {
    myVirtualFileListenerMulticaster.removeListener(listener);
  }

  public void dispatchPendingEvent(@NotNull VirtualFileListener listener) {
    myVirtualFileListenerMulticaster.dispatchPendingEvent(listener);
  }

  public void addModificationAttemptListener(@NotNull ModificationAttemptListener listener) {
    myModificationAttemptListenerMulticaster.addListener(listener);
  }

  public void removeModificationAttemptListener(@NotNull ModificationAttemptListener listener) {
    myModificationAttemptListenerMulticaster.removeListener(listener);
  }

  public void fireReadOnlyModificationAttempt(@NotNull VirtualFile... files) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final ModificationAttemptEvent event = new ModificationAttemptEvent(this, files);
    myModificationAttemptListenerMulticaster.getMulticaster().readOnlyModificationAttempt(event);
  }

  public void addVirtualFileManagerListener(VirtualFileManagerListener listener) {
    myVirtualFileManagerListeners.add(listener);
  }

  public void removeVirtualFileManagerListener(VirtualFileManagerListener listener) {
    myVirtualFileManagerListeners.remove(listener);
  }

  public void fireBeforeRefreshStart(boolean asynchronous) {
    for (final VirtualFileManagerListener listener : myVirtualFileManagerListeners) {
      listener.beforeRefreshStart(asynchronous);
    }
  }

  public void fireAfterRefreshFinish(boolean asynchronous) {
    for (final VirtualFileManagerListener listener : myVirtualFileManagerListeners) {
      listener.afterRefreshFinish(asynchronous);
    }
  }

  public void beforeRefreshStart(final boolean asynchronous, ModalityState modalityState, final Runnable postAction) {
  }

  public void afterRefreshFinish(final boolean asynchronous, final ModalityState modalityState) {
  }

  public void addEventToFireByRefresh(final Runnable action, boolean asynchronous, ModalityState modalityState) {
  }

  public void registerFileContentProvider(FileContentProvider provider) {
    myContentProviders.add(provider);
    myContentProvidersDispatcher.addListener(provider.getVirtualFileListener());
  }

  public void unregisterFileContentProvider(FileContentProvider provider) {
    myContentProviders.remove(provider);
    myContentProvidersDispatcher.removeListener(provider.getVirtualFileListener());
  }

  public void registerRefreshUpdater(CacheUpdater updater) {
    RefreshQueue.getInstance().registerRefreshUpdater(updater);
  }

  public void unregisterRefreshUpdater(CacheUpdater updater) {
    RefreshQueue.getInstance().unregisterRefreshUpdater(updater);
  }

  public ProvidedContent getProvidedContent(VirtualFile file) {
    for (FileContentProvider provider : myContentProviders) {
      // not needed because of special order!
      //dispatchPendingEvent(provider.getVirtualFileListener());
      for (VirtualFile coveredDirectory : provider.getCoveredDirectories()) {
        if (VfsUtil.isAncestor(coveredDirectory, file, true)) {
          return provider.getProvidedContent(file);
        }
      }
    }

    return null;
  }

  public static ModalityState calcModalityStateForRefreshEventsPosting(final boolean asynchronous) {
    return asynchronous ? ModalityState.NON_MODAL : ModalityState.current();
  }

  private static String convertLocalPathToUrl(@NonNls @NotNull String path) {
    if (path.startsWith("~")) {
      path = System.getProperty(USER_HOME) + path.substring(1);
    }

    if (SystemInfo.isWindows || SystemInfo.isOS2) {
      if (path.endsWith(":/")) { // instead of getting canonical path - see below
        path = Character.toUpperCase(path.charAt(0)) + path.substring(1);
      }
    }

    if (path.length() == 0) {
      try {
        path = new File("").getCanonicalPath();
      }
      catch (IOException e) {
        return null;
      }
    }

    if (SystemInfo.isWindows) {
      if (path.charAt(0) == '/') path = path.substring(1); //hack over new File(path).toUrl().getFile()
      if (path.contains("~")) {
        try {
          path = new File(path.replace('/', File.separatorChar)).getCanonicalPath().replace(File.separatorChar, '/');
        }
        catch (IOException e) {
          return null;
        }
      }
    }

    return LocalFileSystem.PROTOCOL + "://" + path.replace(File.separatorChar, '/');
  }

  private static class LoggingListener implements VirtualFileListener {
    public void propertyChanged(VirtualFilePropertyEvent event) {
      LOG.debug("propertyChanged: file = " + event.getFile().getUrl() + ", propertyName = " + event.getPropertyName() + ", oldValue = " +
                event.getOldValue() + ", newValue = " + event.getNewValue() + ", requestor = " + event.getRequestor());
    }

    public void contentsChanged(VirtualFileEvent event) {
      LOG.debug("contentsChanged: file = " + event.getFile().getUrl() + ", requestor = " + event.getRequestor());
    }

    public void fileCreated(VirtualFileEvent event) {
      LOG.debug("fileCreated: file = " + event.getFile().getUrl() + ", requestor = " + event.getRequestor());
    }

    public void fileDeleted(VirtualFileEvent event) {
      final VirtualFile parent = event.getParent();  
      LOG.debug("fileDeleted: file = " + event.getFile().getName() + ", parent = " + (parent != null ? parent.getUrl() : null) +
                ", requestor = " + event.getRequestor());
    }

    public void fileMoved(VirtualFileMoveEvent event) {
      LOG.debug("fileMoved: file = " + event.getFile().getUrl() + ", oldParent = " + event.getOldParent() + ", newParent = " +
                event.getNewParent() + ", requestor = " + event.getRequestor());
    }

    public void fileCopied(VirtualFileCopyEvent event) {
      LOG.debug("fileCopied: file = " + event.getFile().getUrl() + "originalFile = " + event.getOriginalFile().getUrl() + ", requestor = " +
                event.getRequestor());
    }

    public void beforeContentsChange(VirtualFileEvent event) {
      LOG.debug("beforeContentsChange: file = " + event.getFile().getUrl() + ", requestor = " + event.getRequestor());
    }

    public void beforePropertyChange(VirtualFilePropertyEvent event) {
      LOG.debug("beforePropertyChange: file = " + event.getFile().getUrl() + ", propertyName = " + event.getPropertyName() +
                ", oldValue = " + event.getOldValue() + ", newValue = " + event.getNewValue() + ", requestor = " + event.getRequestor());
    }

    public void beforeFileDeletion(VirtualFileEvent event) {
      LOG.debug("beforeFileDeletion: file = " + event.getFile().getUrl() + ", requestor = " + event.getRequestor());

      LOG.assertTrue(event.getFile().isValid());
    }

    public void beforeFileMovement(VirtualFileMoveEvent event) {
      LOG.debug("beforeFileMovement: file = " + event.getFile().getUrl() + ", oldParent = " + event.getOldParent() + ", newParent = " +
                event.getNewParent() + ", requestor = " + event.getRequestor());
    }
  }
}
