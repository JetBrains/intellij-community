package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy;
import com.intellij.openapi.vcs.impl.ExcludedFileIndex;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
  private final ProjectLevelVcsManagerImpl myVcsManager;
  private final ExcludedFileIndex myExcludedFileIndex;
  private boolean myIsDisposed = false;
  private boolean myIsInitialized = false;
  private boolean myEverythingDirty = false;

  public VcsDirtyScopeManagerImpl(Project project,
                                  ChangeListManager changeListManager,
                                  ProjectLevelVcsManager vcsManager,
                                  final ExcludedFileIndex excludedFileIndex) {
    myExcludedFileIndex = excludedFileIndex;
    myProject = project;
    myChangeListManager = changeListManager;
    myVcsManager = (ProjectLevelVcsManagerImpl)vcsManager;
    myVfsListener = new MyVfsListener();
  }

  public void projectOpened() {
    VirtualFileManager.getInstance().addVirtualFileListener(myVfsListener);

    if (ApplicationManager.getApplication().isUnitTestMode()) {
      myIsInitialized = true;
      final AbstractVcs[] vcss = myVcsManager.getAllActiveVcss();
      if (vcss.length > 0) {
        markEverythingDirty();
      }
    }
    else {
      StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
        public void run() {
          myIsInitialized = true;
          markEverythingDirty();
        }
      });
    }
  }

  public void markEverythingDirty() {
    if (!myIsInitialized || myIsDisposed || myProject.isDisposed()) return;

    synchronized(myScopes) {
      // avoid having leftover scopes or invalid roots in scopes after a directory mapping change (IDEADEV-17166)
      myScopes.clear();
    }

    DefaultVcsRootPolicy.getInstance(myProject).markDefaultRootsDirty(this);

    synchronized(myScopes) {
      myEverythingDirty = true;
    }
    myChangeListManager.scheduleUpdate();
  }

  public void projectClosed() {
    myIsDisposed = true;
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

  @Nullable
  private AbstractVcs getVcsForDirty(final VirtualFile file) {
    if (!myIsInitialized || myIsDisposed) {
      return null;
    }
    if (!file.isInLocalFileSystem()) {
      return null;
    }
    if (myExcludedFileIndex.isInContent(file) || isFileInBaseDir(file) ||
        myVcsManager.hasExplicitMapping(file)) {
      if (myExcludedFileIndex.isExcludedFile(file)) {
        return null;
      }
      return myVcsManager.getVcsFor(file);
    }
    return null;
  }

  @Nullable
  private AbstractVcs getVcsForDirty(final FilePath filePath) {
    if (!myIsInitialized || myIsDisposed) {
      return null;
    }
    if (filePath.isNonLocal()) {
      return null;
    }
    final VirtualFile validParent = ChangesUtil.findValidParent(filePath);
    if (validParent == null) {
      return null;
    }
    if (myExcludedFileIndex.isInContent(validParent) || isFileInBaseDir(filePath) ||
        myVcsManager.hasExplicitMapping(filePath)) {
      if (myExcludedFileIndex.isExcludedFile(validParent)) {
        return null;
      }
      return myVcsManager.getVcsFor(validParent);
    }
    return null;
  }

  private boolean isFileInBaseDir(final VirtualFile file) {
    VirtualFile parent = file.getParent();
    return !file.isDirectory() && parent != null && parent.equals(myProject.getBaseDir());
  }

  private boolean isFileInBaseDir(final FilePath filePath) {
    final VirtualFile parent = filePath.getVirtualFileParent(); 
    return !filePath.isDirectory() && parent != null && parent.equals(myProject.getBaseDir());
  }

  public void fileDirty(VirtualFile file) {
    AbstractVcs vcs = getVcsForDirty(file);
    if (vcs != null) {
      getScope(vcs).addDirtyFile(new FilePathImpl(file));
      myChangeListManager.scheduleUpdate();
    }
  }

  public void fileDirty(FilePath file) {
    AbstractVcs vcs = getVcsForDirty(file);
    if (vcs != null) {
      getScope(vcs).addDirtyFile(file);
      myChangeListManager.scheduleUpdate();
    }
  }

  public void dirDirtyRecursively(final VirtualFile dir, final boolean scheduleUpdate) {
    dirDirtyRecursively(dir);
  }

  public void dirDirtyRecursively(final VirtualFile dir) {
    AbstractVcs vcs = getVcsForDirty(dir);
    if (vcs != null) {
      getScope(vcs).addDirtyDirRecursively(new FilePathImpl(dir));
      myChangeListManager.scheduleUpdate();
    }
  }

  public void dirDirtyRecursively(final FilePath path) {
    AbstractVcs vcs = getVcsForDirty(path);
    if (vcs != null) {
      getScope(vcs).addDirtyDirRecursively(path);
      myChangeListManager.scheduleUpdate();
    }
  }

  @NotNull
  public VcsDirtyScopeImpl getScope(final AbstractVcs vcs) {
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
