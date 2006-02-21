package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.*;
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
  private final Map<VirtualFile, VcsDirtyScope> myScopes = new HashMap<VirtualFile, VcsDirtyScope>();
  private final VcsDirtyScopeManagerImpl.MyVfsListener myVfsListener;
  private final Project myProject;
  private final ChangeListManager myChangeListManager;
  private boolean myIsDisposed = false;
  private boolean myIsInitialized = false;

  public VcsDirtyScopeManagerImpl(Project project, ProjectRootManager rootManager, ChangeListManager changeListManager) {
    myProject = project;
    myChangeListManager = changeListManager;
    myIndex = rootManager.getFileIndex();
    myVfsListener = new MyVfsListener();
  }

  public void projectOpened() {
    if (((ApplicationEx)ApplicationManager.getApplication()).isInternal()) {
      VirtualFileManager.getInstance().addVirtualFileListener(myVfsListener);

      StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
        public void run() {
          myIsInitialized = true;
          markEverythingDirty();
        }
      });
    }
  }

  public void markEverythingDirty() {
    if (!myIsInitialized || myIsDisposed) return;
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
      VirtualFile[] roots = rootManager.getContentRoots();
      for (VirtualFile root : roots) {
        dirDirtyRecursively(root);
      }
    }
  }

  public void projectClosed() {
    myIsDisposed = true;
    VirtualFileManager.getInstance().removeVirtualFileListener(myVfsListener);
  }

  @NonNls
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

    VirtualFile root = VcsDirtyScope.getRootFor(myIndex, file);
    if (root != null) {
      getScope(root).addDirtyFile(file);
      myChangeListManager.scheduleUpdate();
    }
  }

  public void dirDirtyRecursively(final VirtualFile dir) {
    if (!myIsInitialized || myIsDisposed) return;

    final VirtualFile root = myIndex.getContentRootForFile(dir);
    if (root != null) {
      FilePath path = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(dir);
      getScope(root).addDirtyDirRecursively(path);
      myChangeListManager.scheduleUpdate();
    }
  }

  @NotNull
  private VcsDirtyScope getScope(final VirtualFile root) {
    synchronized (myScopes) {
      VcsDirtyScope scope = myScopes.get(root);
      if (scope == null) {
        scope = new VcsDirtyScope(root, myProject);
        myScopes.put(root, scope);
      }
      return scope;
    }
  }

  public List<VcsDirtyScope> retreiveScopes() {
    synchronized (myScopes) {
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
