package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.peer.PeerFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author max
 */
public class VcsDirtyScopeManagerImpl extends VcsDirtyScopeManager implements ProjectComponent {
  private final ProjectFileIndex myIndex;
  private final Map<AbstractVcs, VcsDirtyScopeImpl> myScopes = new HashMap<AbstractVcs, VcsDirtyScopeImpl>();
  private final VcsDirtyScopeManagerImpl.MyVfsListener myVfsListener;
  private final Project myProject;
  private final ProjectRootManager myRootManager;
  private final ChangeListManager myChangeListManager;
  private final ProjectLevelVcsManager myVcsManager;
  private boolean myIsDisposed = false;
  private boolean myIsInitialized = false;
  private boolean myEverythingDirty = false;
  private ModuleRootListener myRootModelListener;

  public VcsDirtyScopeManagerImpl(Project project,
                                  ProjectRootManager rootManager,
                                  ChangeListManager changeListManager,
                                  ProjectLevelVcsManager vcsManager) {
    myProject = project;
    myRootManager = rootManager;
    myChangeListManager = changeListManager;
    myVcsManager = vcsManager;
    myIndex = rootManager.getFileIndex();
    myVfsListener = new MyVfsListener();
  }

  public void projectOpened() {
    VirtualFileManager.getInstance().addVirtualFileListener(myVfsListener);

    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        myIsInitialized = true;
        markEverythingDirty();
      }
    });

    myRootModelListener = new ModuleRootListener() {
      public void beforeRootsChange(ModuleRootEvent event) {
      }

      public void rootsChanged(ModuleRootEvent event) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            markEverythingDirty();
          }
        }, ModalityState.NON_MODAL);
      }
    };

    myRootManager.addModuleRootListener(myRootModelListener);
  }

  public void markEverythingDirty() {
    if (!myIsInitialized || myIsDisposed || myProject.isDisposed()) return;
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      VirtualFile[] roots = rootManager.getContentRoots();
      for (VirtualFile root : roots) {
        dirDirtyRecursively(root, true);
      }
    }
    synchronized(myScopes) {
      myEverythingDirty = true;
    }
  }

  public void projectClosed() {
    myIsDisposed = true;
    myRootManager.removeModuleRootListener(myRootModelListener);
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
    fileDirty(PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(file));
  }

  public void fileDirty(FilePath file) {
    if (!myIsInitialized || myIsDisposed) return;

    ApplicationManager.getApplication().assertReadAccessAllowed();
    VirtualFile root = VcsDirtyScope.getRootFor(myIndex, file);
    if (root != null) {
      getScope(root).addDirtyFile(file);
      myChangeListManager.scheduleUpdate();
    }
  }

  public void dirDirtyRecursively(final VirtualFile dir, final boolean scheduleUpdate) {
    if (!myIsInitialized || myIsDisposed) return;

    final VirtualFile root = myIndex.getContentRootForFile(dir);
    if (root != null) {
      FilePath path = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(dir);
      getScope(root).addDirtyDirRecursively(path);
      myChangeListManager.scheduleUpdate();
    }
  }

  @NotNull
  private VcsDirtyScopeImpl getScope(final VirtualFile root) {
    synchronized (myScopes) {
      final AbstractVcs vcs = myVcsManager.getVcsFor(root);
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

  public List<VcsDirtyScope> retreiveScopes() {
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
      fileDirty(event.getFile());
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
      fileDirty(event.getFile());
    }

    @Override
    public void beforeFileMovement(VirtualFileMoveEvent event) {
      fileDirty(event.getFile());
    }

    @Override
    public void fileMoved(VirtualFileMoveEvent event) {
      fileDirty(event.getFile());
    }
  }
}
