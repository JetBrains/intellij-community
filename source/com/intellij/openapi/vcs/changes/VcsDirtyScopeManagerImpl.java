package com.intellij.openapi.vcs.changes;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.*;
import com.intellij.peer.PeerFactory;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author max
 */
public class VcsDirtyScopeManagerImpl extends VcsDirtyScopeManager implements ProjectComponent {
  private final Map<AbstractVcs, VcsDirtyScopeImpl> myScopes = new HashMap<AbstractVcs, VcsDirtyScopeImpl>();
  private final VcsDirtyScopeManagerImpl.MyVfsListener myVfsListener;
  private final Project myProject;
  private final ChangeListManager myChangeListManager;
  private final ProjectLevelVcsManager myVcsManager;
  private boolean myIsDisposed = false;
  private boolean myIsInitialized = false;
  private boolean myEverythingDirty = false;
  private MessageBusConnection myConnection;

  public VcsDirtyScopeManagerImpl(Project project,
                                  ChangeListManager changeListManager,
                                  ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myChangeListManager = changeListManager;
    myVcsManager = vcsManager;
    myVfsListener = new MyVfsListener();
  }

  public void projectOpened() {
    VirtualFileManager.getInstance().addVirtualFileListener(myVfsListener);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myIsInitialized = true;
      markEverythingDirty();
    }
    else {
      StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
        public void run() {
          myIsInitialized = true;
          markEverythingDirty();
        }
      });
    }

    myConnection = myProject.getMessageBus().connect();
    myConnection.subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            markEverythingDirty();
          }
        }, ModalityState.NON_MODAL);
      }
    });
  }

  public void markEverythingDirty() {
    if (!myIsInitialized || myIsDisposed || myProject.isDisposed()) return;

    synchronized(myScopes) {
      // avoid having leftover scopes or invalid roots in scopes after a directory mapping change (IDEADEV-17166)
      myScopes.clear();
    }
    final AbstractVcs[] abstractVcses = myVcsManager.getAllActiveVcss();
    for(AbstractVcs vcs: abstractVcses) {
      VcsDirtyScopeImpl scope = getScope(vcs);
      final VirtualFile[] roots = myVcsManager.getRootsUnderVcs(vcs);
      for(VirtualFile root: roots) {
        scope.addDirtyDirRecursively(new FilePathImpl(root));
      }
    }
    synchronized(myScopes) {
      myEverythingDirty = true;
    }
    myChangeListManager.scheduleUpdate();
  }

  public void projectClosed() {
    myIsDisposed = true;
    myConnection.disconnect();
    VirtualFileManager.getInstance().removeVirtualFileListener(myVfsListener);
  }

  @NotNull @NonNls
  public String getComponentName() {
    return "VcsDirtyScopeManager";
  }

  public void initComponent() {}

  public void disposeComponent() {
    myIsDisposed = true;
  }

  public void fileDirty(VirtualFile file) {
    if (!file.isInLocalFileSystem()) {
      return;
    }
    fileDirty(PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(file));
  }

  public void fileDirty(FilePath file) {
    if (!myIsInitialized || myIsDisposed) return;

    ApplicationManager.getApplication().assertReadAccessAllowed();
    AbstractVcs vcs = myVcsManager.getVcsFor(file);
    if (vcs != null) {
      getScope(vcs).addDirtyFile(file);
      myChangeListManager.scheduleUpdate();
    }
  }

  public void dirDirtyRecursively(final VirtualFile dir, final boolean scheduleUpdate) {
    final FilePathImpl path = new FilePathImpl(dir);
    dirDirtyRecursively(path);
  }

  private void dirDirtyRecursively(final FilePathImpl path) {
    if (!myIsInitialized || myIsDisposed) return;

    final AbstractVcs vcs = myVcsManager.getVcsFor(path);
    if (vcs != null) {
      getScope(vcs).addDirtyDirRecursively(path);
      myChangeListManager.scheduleUpdate();
    }
  }

  @NotNull
  private VcsDirtyScopeImpl getScope(final AbstractVcs vcs) {
    synchronized (myScopes) {
      VcsDirtyScopeImpl scope = myScopes.get(vcs);
      if (scope == null) {
        scope = new VcsDirtyScopeImpl(vcs, myProject);
        myScopes.put(vcs, scope);
      }
      return scope;
    }
  }

  public boolean isEverythingDirty() {
    synchronized(myScopes) {
      return myEverythingDirty;
    }
  }

  public List<VcsDirtyScope> retrieveScopes() {
    synchronized (myScopes) {
      myEverythingDirty = false;
      final ArrayList<VcsDirtyScope> result = new ArrayList<VcsDirtyScope>(myScopes.values());
      myScopes.clear();
      return result;
    }
  }

  private class MyVfsListener extends VirtualFileAdapter {
    @Override
    public void contentsChanged(VirtualFileEvent event) {
      fileDirty(event.getFile());
    }

    @Override
    public void propertyChanged(VirtualFilePropertyEvent event) {
      if (event.getPropertyName().equals(VirtualFile.PROP_NAME)) {
        dirtyFileOrDir(event.getFile());
      }
      else {
        fileDirty(event.getFile());
      }
    }

    private void dirtyFileOrDir(final VirtualFile file) {
      if (file.isDirectory()) {
          dirDirtyRecursively(file, true);
        }
        else {
          fileDirty(file);
        }
    }

    @Override
    public void fileCreated(VirtualFileEvent event) {
      fileDirty(event.getFile());
    }

    @Override
    public void beforePropertyChange(VirtualFilePropertyEvent event) {
      fileDirty(event.getFile());
    }

    @Override
    public void beforeFileDeletion(VirtualFileEvent event) {
      if (!event.getFile().isInLocalFileSystem()) return;
      // need to keep track of whether the deleted file was a directory
      final boolean directory = event.getFile().isDirectory();
      final FilePathImpl path = new FilePathImpl(new File(event.getFile().getPath()), directory);
      if (directory) {
        dirDirtyRecursively(path);   // IDEADEV-12752
      }
      else {
        fileDirty(path);
      }
    }

    @Override
    public void beforeFileMovement(VirtualFileMoveEvent event) {
      // need to create FilePath explicitly without referring to VirtualFile because otherwise the FilePath
      // will reference the path after the move
      fileDirty(new FilePathImpl(new File(event.getFile().getPath()), event.getFile().isDirectory()));
    }

    @Override
    public void fileMoved(VirtualFileMoveEvent event) {
      dirtyFileOrDir(event.getFile());
    }
  }
}
