package org.jetbrains.idea.svn.integrate;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.changes.VcsDirtyScope;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class AlienDirtyScope extends VcsDirtyScope {
  private final Set<FilePath> myFiles;
  private final Set<FilePath> myDirs;

  public AlienDirtyScope() {
    myFiles = new HashSet<FilePath>();
    myDirs = new HashSet<FilePath>();
  }

  public Collection<VirtualFile> getAffectedContentRoots() {
    return null;
  }

  public Project getProject() {
    return null;
  }

  public AbstractVcs getVcs() {
    return null;
  }

  public Set<FilePath> getDirtyFiles() {
    return myFiles;
  }

  public Set<FilePath> getDirtyFilesNoExpand() {
    return null;
  }

  public Set<FilePath> getRecursivelyDirtyDirectories() {
    return myDirs;
  }

  public void iterate(final Processor<FilePath> iterator) {
  }

  public boolean belongsTo(final FilePath path) {
    return false;
  }

  public void addFile(final FilePath path) {
    myFiles.add(path);
  }

  public void addDir(final FilePath dir) {
    myDirs.add(dir);
  }
}
