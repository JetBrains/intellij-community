/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 12.07.2006
 * Time: 14:34:37
 */
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.peer.PeerFactory;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class VcsDirtyScopeImpl extends VcsDirtyScope {
  private final Set<FilePath> myDirtyFiles = new THashSet<FilePath>();
  private final Set<FilePath> myDirtyDirectoriesRecursively = new THashSet<FilePath>();
  private final Set<VirtualFile> myAffectedContentRoots = new THashSet<VirtualFile>();
  private final ProjectFileIndex myIndex;
  private Project myProject;
  private AbstractVcs myVcs;

  public VcsDirtyScopeImpl(final AbstractVcs vcs, final Project project) {
    myProject = project;
    myVcs = vcs;
    myIndex = ProjectRootManager.getInstance(project).getFileIndex();
  }

  public Collection<VirtualFile> getAffectedContentRoots() {
    return myAffectedContentRoots;
  }

  public AbstractVcs getVcs() {
    return myVcs;
  }

  public Set<FilePath> getDirtyFiles() {
    return myDirtyFiles;
  }

  public Set<FilePath> getRecursivelyDirtyDirectories() {
    return myDirtyDirectoriesRecursively;
  }

  public void addDirtyDirRecursively(FilePath newcomer) {
    myAffectedContentRoots.add(getRootFor(myIndex, newcomer));

    for (FilePath oldBoy : myDirtyDirectoriesRecursively) {
      if (newcomer.isUnder(oldBoy, false)) {
        return;
      }

      if (oldBoy.isUnder(newcomer, false)) {
        myDirtyDirectoriesRecursively.remove(oldBoy);
        myDirtyDirectoriesRecursively.add(newcomer);
        return;
      }
    }
    myDirtyDirectoriesRecursively.add(newcomer);
  }

  public void addDirtyFile(FilePath newcomer) {
    myAffectedContentRoots.add(getRootFor(myIndex, newcomer));

    for (FilePath oldBoy : myDirtyDirectoriesRecursively) {
      if (newcomer.isUnder(oldBoy, false)) {
        return;
      }
    }

    if (newcomer.isDirectory()) {
      final List<FilePath> files = new ArrayList<FilePath>(myDirtyFiles);
      for (FilePath oldBoy : files) {
        if (!oldBoy.isDirectory() && oldBoy.getVirtualFileParent() == newcomer.getVirtualFile()) {
          myDirtyFiles.remove(oldBoy);
        }
      }
    }
    else {
      for (FilePath oldBoy : myDirtyFiles) {
        if (oldBoy.isDirectory() && newcomer.getVirtualFileParent() == oldBoy.getVirtualFile()) {
          return;
        }
      }
    }

    myDirtyFiles.add(newcomer);
  }

  public void iterate(ContentIterator iterator) {
    if (myProject.isDisposed()) return;

    for (VirtualFile root : myAffectedContentRoots) {
      final Module module = VfsUtil.getModuleForFile(myProject, root);
      if (module == null) continue; // Roots probably change. We'll handle this in next dirty scope processing iteration.

      final ModuleFileIndex index = ModuleRootManager.getInstance(module).getFileIndex();

      for (FilePath dir : myDirtyDirectoriesRecursively) {
        final VirtualFile vFile = dir.getVirtualFile();
        if (vFile != null && vFile.isValid()) {
          if (VfsUtil.isAncestor(root, vFile, false)) {
            index.iterateContentUnderDirectory(vFile, iterator);
          }
          else if (VfsUtil.isAncestor(vFile, root, false)) {
            index.iterateContentUnderDirectory(root, iterator);
          }
        }
      }
    }

    for (FilePath file : myDirtyFiles) {
      final VirtualFile vFile = file.getVirtualFile();
      if (vFile != null && vFile.isValid()) {
        iterator.processFile(vFile);
        if (vFile.isDirectory()) {
          for (VirtualFile child : vFile.getChildren()) {
            iterator.processFile(child);
          }
        }
      }
    }
  }

  public boolean belongsTo(final FilePath path) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        if (myProject.isDisposed()) return Boolean.FALSE;
        if (!myAffectedContentRoots.contains(getRootFor(myIndex, path))) return Boolean.FALSE;

        for (FilePath filePath : myDirtyDirectoriesRecursively) {
          if (path.isUnder(filePath, false)) return Boolean.TRUE;
        }

        FilePath parent = PeerFactory.getInstance().getVcsContextFactory().createFilePathOn(path.getIOFile().getParentFile());
        for (FilePath filePath : myDirtyFiles) {
          if (filePath.equals(parent) || filePath.equals(path)) return Boolean.TRUE;
        }

        return Boolean.FALSE;
      }
    }).booleanValue();
  }

  public void refreshDirtyFiles() {
    boolean needRefreshVFS = false;
    for(final FilePath file: myDirtyFiles) {
      if (file.getVirtualFile() == null) {
        needRefreshVFS = true;
      }
    }
    if (needRefreshVFS) {
      ApplicationManager.getApplication().invokeAndWait(new Runnable() {
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              for(final FilePath file: myDirtyFiles) {
                if (file.getVirtualFile() == null) {
                  VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file.getIOFile());
                  if (vFile != null && file.isDirectory()) {
                    vFile.refresh(false, true);
                  }
                }
              }
            }
          });
        }
      }, ModalityState.defaultModalityState());
    }
  }

  @Override @NonNls
  public String toString() {
    @NonNls StringBuilder result = new StringBuilder("VcsDirtyScope[");
    if (myDirtyFiles.size() > 0) {
      result.append(" files=");
      for(FilePath file: myDirtyFiles) {
        result.append(file).append(" ");
      }
    }
    if (myDirtyDirectoriesRecursively.size() > 0) {
      result.append(" dirs=");
      for(FilePath file: myDirtyDirectoriesRecursively) {
        result.append(file).append(" ");
      }
    }
    result.append("]");
    return result.toString();
  }
}
