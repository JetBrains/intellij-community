package com.intellij.openapi.vfs.impl;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.ide.startup.FileSystemSynchronizer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.StatusBarProgress;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.ex.FileContentProvider;
import com.intellij.openapi.vfs.ex.ProvidedContent;
import com.intellij.openapi.vfs.ex.VirtualFileManagerEx;
import com.intellij.util.PendingEventDispatcher;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.Stack;

public class VirtualFileManagerImpl extends VirtualFileManagerEx implements ApplicationComponent {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vfs.impl.VirtualFileManagerImpl");

  private ArrayList<VirtualFileSystem> myFileSystems = null;
  private HashMap<String, VirtualFileSystem> myProtocolToSystemMap = null;

  private PendingEventDispatcher<VirtualFileListener> myVirtualFileListenerMulticaster = PendingEventDispatcher.create(VirtualFileListener.class);
  private PendingEventDispatcher<VirtualFileManagerListener> myVirtualFileManagerListenerMulticaster = PendingEventDispatcher.create(VirtualFileManagerListener.class);
  private PendingEventDispatcher<ModificationAttemptListener> myModificationAttemptListenerMulticaster = PendingEventDispatcher.create(ModificationAttemptListener.class);

  private ProgressIndicator myRefreshIndicator = new StatusBarProgress();
  private ProgressIndicator myAsyncRefreshIndicator = new StatusBarProgress();

  private ArrayList<FileContentProvider> myContentProviders = new ArrayList<FileContentProvider>();
  private PendingEventDispatcher<VirtualFileListener> myContentProvidersDispatcher = PendingEventDispatcher.create(VirtualFileListener.class);
  private ArrayList<CacheUpdater> myRefreshParticipants = new ArrayList<CacheUpdater>();

  private int myRefreshCount = 0;
  private int mySynchronousRefreshCount = 0;
  private ArrayList<Runnable> myRefreshEventsToFire = null;
  private Stack<Runnable> myPostRefreshRunnables = new Stack<Runnable>();
  private final ProgressManager myProgressManager;


  public VirtualFileManagerImpl(ProgressManager progressManager, VirtualFileSystem[] fileSystems) {
    myProgressManager = progressManager;
    myFileSystems = new ArrayList<VirtualFileSystem>();
    myProtocolToSystemMap = new HashMap<String, VirtualFileSystem>();
    for (VirtualFileSystem fileSystem : fileSystems) {
      registerFileSystem(fileSystem);
    }

    if (LOG.isDebugEnabled()) {
      addVirtualFileListener(new LoggingListener());
    }
    addVirtualFileListener(myContentProvidersDispatcher.getMulticaster());

  }

  public String getComponentName() {
    return "VirtualFileManager";
  }

  public void initComponent() { }

  public void disposeComponent() {
  }

  private void registerFileSystem(VirtualFileSystem fileSystem) {
    myFileSystems.add(fileSystem);
    fileSystem.addVirtualFileListener(myVirtualFileListenerMulticaster.getMulticaster());
    myProtocolToSystemMap.put(fileSystem.getProtocol(), fileSystem);
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

  public void refresh(boolean asynchronous, final Runnable postAction) {
    final ModalityState modalityState;
    if (EventQueue.isDispatchThread()) {
      modalityState = ModalityState.current();
    }
    else {
      final ProgressIndicator progressIndicator = myProgressManager.getProgressIndicator();
      modalityState = (progressIndicator != null)? progressIndicator.getModalityState() : ModalityState.NON_MMODAL;
    }

    beforeRefreshStart(asynchronous, modalityState, postAction);

    try {
      if (!asynchronous) {
        ApplicationManager.getApplication().assertIsDispatchThread();
      }

      for (VirtualFileSystem fileSystem : myFileSystems) {
        fileSystem.refresh(asynchronous);
      }
    }
    finally {
      afterRefreshFinish(asynchronous, modalityState);
    }
  }

  public VirtualFile findFileByUrl(String url) {
    String protocol = extractProtocol(url);
    if (protocol == null) return null;
    VirtualFileSystem fileSystem = myProtocolToSystemMap.get(protocol);
    if (fileSystem == null) return null;
    return fileSystem.findFileByPath(extractPath(url));
  }

  public VirtualFile refreshAndFindFileByUrl(String url) {
    String protocol = extractProtocol(url);
    if (protocol == null) return null;
    VirtualFileSystem fileSystem = myProtocolToSystemMap.get(protocol);
    if (fileSystem == null) return null;
    String path = extractPath(url);
    return fileSystem.refreshAndFindFileByPath(path);
  }

  public void addVirtualFileListener(VirtualFileListener listener) {
    myVirtualFileListenerMulticaster.addListener(listener);
  }

  public void addVirtualFileListener(@NotNull VirtualFileListener listener, Disposable parentDisposable) {
    myVirtualFileListenerMulticaster.addListener(listener, parentDisposable);
  }

  public void removeVirtualFileListener(VirtualFileListener listener) {
    myVirtualFileListenerMulticaster.removeListener(listener);
  }

  public void dispatchPendingEvent(VirtualFileListener listener) {
    myVirtualFileListenerMulticaster.dispatchPendingEvent(listener);
  }

  public void addModificationAttemptListener(ModificationAttemptListener listener) {
    myModificationAttemptListenerMulticaster.addListener(listener);
  }

  public void removeModificationAttemptListener(ModificationAttemptListener listener) {
    myModificationAttemptListenerMulticaster.removeListener(listener);
  }

  public void fireReadOnlyModificationAttempt(VirtualFile... files) {
    ApplicationManager.getApplication().assertIsDispatchThread();

    final ModificationAttemptEvent event = new ModificationAttemptEvent(this, files);
    myModificationAttemptListenerMulticaster.getMulticaster().readOnlyModificationAttempt(event);
  }

  public void addVirtualFileManagerListener(VirtualFileManagerListener listener) {
    myVirtualFileManagerListenerMulticaster.addListener(listener);
  }

  public void removeVirtualFileManagerListener(VirtualFileManagerListener listener) {
    myVirtualFileManagerListenerMulticaster.removeListener(listener);
  }

  public void beforeRefreshStart(final boolean asynchronous, ModalityState modalityState, final Runnable postAction) {
    Runnable action = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().assertIsDispatchThread();
        final ProgressIndicator indicator = asynchronous ? myAsyncRefreshIndicator : myRefreshIndicator;
        if (asynchronous) {
          if (getAsynchronousRefreshCount() == 0) {
            indicator.start();
          }
        } else {
          if (mySynchronousRefreshCount == 0) {
            indicator.start();
          }
        }
        indicator.setText(VfsBundle.message("file.synchronize.progress"));

        myRefreshCount++;
        if (!asynchronous) mySynchronousRefreshCount++;
        myPostRefreshRunnables.push(postAction);
        if (myRefreshCount == 1) {
          myRefreshEventsToFire = new ArrayList<Runnable>();
          myRefreshEventsToFire.add(new FireBeforeRefresh(asynchronous));
        }
      }
    };
    if (asynchronous) {
      ApplicationManager.getApplication().invokeLater(action, modalityState);
    }
    else {
      action.run();
    }
  }

  private int getAsynchronousRefreshCount() {
    return myRefreshCount - mySynchronousRefreshCount;
  }

  private class FireBeforeRefresh implements Runnable {
    private final boolean myAsynchonous;

    public FireBeforeRefresh(boolean asynchonous) {
      myAsynchonous = asynchonous;
    }

    public void run() {
      myVirtualFileManagerListenerMulticaster.getMulticaster().beforeRefreshStart(myAsynchonous);
    }
  }

  public void afterRefreshFinish(final boolean asynchronous, ModalityState modalityState) {
    Runnable action = new Runnable() {
      public void run() {
        ApplicationManager.getApplication().assertIsDispatchThread();
        Runnable postRunnable = myPostRefreshRunnables.pop();

        ApplicationManager.getApplication().runWriteAction(
          new Runnable() {
            public void run() {
              //noinspection ForLoopReplaceableByForEach
              for (int i = 0; i < myRefreshEventsToFire.size(); i++) {
                Runnable runnable = myRefreshEventsToFire.get(i);
                try {
                  runnable.run();
                }
                catch (Exception e) {
                  LOG.error(e);
                }
              }

              myRefreshCount--;
              if (!asynchronous) mySynchronousRefreshCount--;
              LOG.assertTrue(myRefreshCount >= 0 && mySynchronousRefreshCount >= 0);

              final ProgressIndicator indicator = asynchronous ? myAsyncRefreshIndicator : myRefreshIndicator;
              if (asynchronous) {
                if (getAsynchronousRefreshCount() == 0) {
                  indicator.stop();
                }
              }
              else {
                if (mySynchronousRefreshCount == 0) {
                  indicator.stop();
                }
              }

              if (myRefreshCount > 0) {
                myRefreshEventsToFire.clear();
                if (!asynchronous && mySynchronousRefreshCount == 0) {
                  myVirtualFileManagerListenerMulticaster.getMulticaster().afterRefreshFinish(asynchronous);
                }
              }
              else {
                final FileSystemSynchronizer synchronizer;
                if (asynchronous) {
                  synchronizer = new FileSystemSynchronizer();
                  //noinspection ForLoopReplaceableByForEach
                  for (int i = 0; i < myRefreshParticipants.size(); i++) {
                    CacheUpdater participant = myRefreshParticipants.get(i);
                    synchronizer.registerCacheUpdater(participant);
                  }
                }
                else {
                  synchronizer = null;
                }

                myRefreshEventsToFire = null;
                myVirtualFileManagerListenerMulticaster.getMulticaster().afterRefreshFinish(asynchronous);

                if (asynchronous) {
                  int filesCount = synchronizer.collectFilesToUpdate();
                  if (filesCount > 0) {
                    boolean runWithProgress = !ApplicationManager.getApplication().isUnitTestMode() && filesCount > 5;
                    if (runWithProgress) {
                      Runnable process = new Runnable() {
                        public void run() {
                          synchronizer.execute();
                        }
                      };
                      ProgressManager.getInstance().runProcessWithProgressSynchronously(process, VfsBundle.message(
                        "file.update.modified.progress"), false, null);
                    }
                    else {
                      synchronizer.execute();
                    }
                  }
                }
              }
            }
          }
        );

        if (postRunnable != null){
          postRunnable.run();
        }
      }
    };

    if (asynchronous) {
      ApplicationManager.getApplication().invokeLater(action, modalityState);
    }
    else {
      action.run();
    }
  }

  public void addEventToFireByRefresh(final Runnable action, boolean asynchronous, ModalityState modalityState) {
    if (asynchronous) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              myRefreshEventsToFire.add(action);
            }
          }, modalityState);
    }
    else {
      ApplicationManager.getApplication().assertIsDispatchThread();
      myRefreshEventsToFire.add(action);
    }
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
    myRefreshParticipants.add(updater);
  }

  public void unregisterRefreshUpdater(CacheUpdater updater) {
    boolean success = myRefreshParticipants.remove(updater);
    LOG.assertTrue(success);
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

  private static class LoggingListener implements VirtualFileListener {
    public void propertyChanged(VirtualFilePropertyEvent event) {
      LOG.debug(
        "propertyChanged: file = " + event.getFile().getUrl() +
        ", propertyName = " + event.getPropertyName() +
        ", oldValue = " + event.getOldValue() +
        ", newValue = " + event.getNewValue() +
        ", requestor = " + event.getRequestor()
      );
    }

    public void contentsChanged(VirtualFileEvent event) {
      LOG.debug(
        "contentsChanged: file = " + event.getFile().getUrl() +
        ", requestor = " + event.getRequestor()
      );
    }

    public void fileCreated(VirtualFileEvent event) {
      LOG.debug(
        "fileCreated: file = " + event.getFile().getUrl() +
        ", requestor = " + event.getRequestor()
      );
    }

    public void fileDeleted(VirtualFileEvent event) {
      LOG.debug(
        "fileDeleted: file = " + event.getFile().getName() +
        ", parent = " + (event.getParent() != null ? event.getParent().getUrl() : null) +
        ", requestor = " + event.getRequestor()
      );
    }

    public void fileMoved(VirtualFileMoveEvent event) {
      LOG.debug(
        "fileMoved: file = " + event.getFile().getUrl() +
        ", oldParent = " + event.getOldParent() +
        ", newParent = " + event.getNewParent() +
        ", requestor = " + event.getRequestor()
      );
    }

    public void beforeContentsChange(VirtualFileEvent event) {
      LOG.debug(
        "beforeContentsChange: file = " + event.getFile().getUrl() +
        ", requestor = " + event.getRequestor()
      );
    }

    public void beforePropertyChange(VirtualFilePropertyEvent event) {
      LOG.debug(
        "beforePropertyChange: file = " + event.getFile().getUrl() +
        ", propertyName = " + event.getPropertyName() +
        ", oldValue = " + event.getOldValue() +
        ", newValue = " + event.getNewValue() +
        ", requestor = " + event.getRequestor()
      );
    }

    public void beforeFileDeletion(VirtualFileEvent event) {
      LOG.debug(
        "beforeFileDeletion: file = " + event.getFile().getUrl() +
        ", requestor = " + event.getRequestor()
      );

      LOG.assertTrue(event.getFile().isValid());
    }

    public void beforeFileMovement(VirtualFileMoveEvent event) {
      LOG.debug(
        "beforeFileMovement: file = " + event.getFile().getUrl() +
        ", oldParent = " + event.getOldParent() +
        ", newParent = " + event.getNewParent() +
        ", requestor = " + event.getRequestor()
      );
    }
  }

  public void cleanupForNextTest() {
    myRefreshCount = 0;
    myRefreshEventsToFire = null;
  }
}
