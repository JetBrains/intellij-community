/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FilePathImpl;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * @author max
 * @author yole
 */
public class VcsDirtyScopeImpl extends VcsDirtyScope {
  private final Set<FilePath> myDirtyFiles = new THashSet<FilePath>();
  private final Map<VirtualFile, THashSet<FilePath>> myDirtyDirectoriesRecursively = new HashMap<VirtualFile, THashSet<FilePath>>();
  private final Set<VirtualFile> myAffectedContentRoots = new THashSet<VirtualFile>();
  private final Project myProject;
  private final ProjectLevelVcsManager myVcsManager;
  private final AbstractVcs myVcs;

  public VcsDirtyScopeImpl(final AbstractVcs vcs, final Project project) {
    myProject = project;
    myVcs = vcs;
    myVcsManager = ProjectLevelVcsManager.getInstance(project);
  }

  public Collection<VirtualFile> getAffectedContentRoots() {
    return myAffectedContentRoots;
  }

  public Project getProject() {
    return myProject;
  }

  public AbstractVcs getVcs() {
    return myVcs;
  }

  public synchronized Set<FilePath> getDirtyFiles() {
    final THashSet<FilePath> result = new THashSet<FilePath>(myDirtyFiles);
    for(FilePath filePath: myDirtyFiles) {
      VirtualFile vFile = filePath.getVirtualFile();
      if (vFile != null && vFile.isValid() && vFile.isDirectory()) {
        for(VirtualFile child: vFile.getChildren()) {
          result.add(new FilePathImpl(child));
        }
      }
    }
    return result;
  }

  public Set<FilePath> getDirtyFilesNoExpand() {
    return new THashSet<FilePath>(myDirtyFiles);
  }

  public synchronized Set<FilePath> getRecursivelyDirtyDirectories() {
    THashSet<FilePath> result = new THashSet<FilePath>();
    for(THashSet<FilePath> dirsByRoot: myDirtyDirectoriesRecursively.values()) {
      result.addAll(dirsByRoot);
    }
    return result;
  }

  public void addDirtyDirRecursively(final FilePath newcomer) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        synchronized (VcsDirtyScopeImpl.this) {
          final VirtualFile vcsRoot = myVcsManager.getVcsRootFor(newcomer);
          if (vcsRoot == null) return;
          myAffectedContentRoots.add(vcsRoot);

          for (Iterator<FilePath> it = myDirtyFiles.iterator(); it.hasNext();) {
            FilePath oldBoy = it.next();
            if (oldBoy.isUnder(newcomer, false)) {
              it.remove();
            }
          }

          THashSet<FilePath> dirsByRoot = myDirtyDirectoriesRecursively.get(vcsRoot);
          if (dirsByRoot == null) {
            dirsByRoot = new THashSet<FilePath>();
            myDirtyDirectoriesRecursively.put(vcsRoot, dirsByRoot);
          }
          else {
            for (Iterator<FilePath> it = dirsByRoot.iterator(); it.hasNext();) {
              FilePath oldBoy = it.next();
              if (newcomer.isUnder(oldBoy, false)) {
                return;
              }

              if (oldBoy.isUnder(newcomer, false)) {
                it.remove();
              }
            }
          }

          dirsByRoot.add(newcomer);
        }
      }
    });
  }

  public void addDirtyFile(final FilePath newcomer) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        synchronized (VcsDirtyScopeImpl.this) {
          final VirtualFile vcsRoot = myVcsManager.getVcsRootFor(newcomer);
          myAffectedContentRoots.add(vcsRoot);

          THashSet<FilePath> dirsByRoot = myDirtyDirectoriesRecursively.get(vcsRoot);
          if (dirsByRoot != null) {
            for (FilePath oldBoy : dirsByRoot) {
              if (newcomer.isUnder(oldBoy, false)) {
                return;
              }
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
      }
    });
  }

  public synchronized void iterate(final Processor<FilePath> iterator) {
    if (myProject.isDisposed()) return;

    for (VirtualFile root : myAffectedContentRoots) {
      THashSet<FilePath> dirsByRoot = myDirtyDirectoriesRecursively.get(root);
      if (dirsByRoot != null) {
        for (FilePath dir : dirsByRoot) {
          final VirtualFile vFile = dir.getVirtualFile();
          if (vFile != null && vFile.isValid()) {
            myVcsManager.iterateVcsRoot(vFile, iterator);
          }
        }
      }
    }

    for (FilePath file : myDirtyFiles) {
      iterator.process(file);
      final VirtualFile vFile = file.getVirtualFile();
      if (vFile != null && vFile.isValid() && vFile.isDirectory()) {
        for (VirtualFile child : vFile.getChildren()) {
          iterator.process(new FilePathImpl(child));
        }
      }
    }
  }

  public boolean belongsTo(final FilePath path) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        synchronized (VcsDirtyScopeImpl.this) {
          if (myProject.isDisposed()) return Boolean.FALSE;
          if (myVcsManager.getVcsFor(path) != myVcs) {
            return Boolean.FALSE;
          }

          final VirtualFile vcsRoot = myVcsManager.getVcsRootFor(path);
          if (vcsRoot != null) {
            for(VirtualFile contentRoot: myAffectedContentRoots) {
              if (VfsUtil.isAncestor(contentRoot, vcsRoot, false)) {
                THashSet<FilePath> dirsByRoot = myDirtyDirectoriesRecursively.get(contentRoot);
                if (dirsByRoot != null) {
                  for (FilePath filePath : dirsByRoot) {
                    if (path.isUnder(filePath, false)) return Boolean.TRUE;
                  }
                }
                break;
              }
            }
          }

          if (myDirtyFiles.size() > 0) {
            FilePath parent;
            VirtualFile vParent = path.getVirtualFileParent();
            if (vParent != null && vParent.isValid()) {
              parent = new FilePathImpl(vParent);
            }
            else {
              parent = FilePathImpl.create(path.getIOFile().getParentFile());
            }
            if (myDirtyFiles.contains(parent) || myDirtyFiles.contains(path)) return Boolean.TRUE;
          }

          return Boolean.FALSE;
        }
      }
    }).booleanValue();
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
      for(THashSet<FilePath> dirsByRoot: myDirtyDirectoriesRecursively.values()) {
        for(FilePath file: dirsByRoot) {
          result.append(file).append(" ");
        }
      }
    }
    result.append("]");
    return result.toString();
  }
}
