/*
 * @author: Eugene Zhuravlev
 * Date: Jan 20, 2003
 * Time: 5:34:19 PM
 */
package com.intellij.compiler.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class FileSetCompileScope implements CompileScope {
  private Set<VirtualFile> myRootFiles = new HashSet<VirtualFile>();
  private Set<String> myDirectoryUrls = new HashSet<String>();
  private Set<String> myUrls = null; // urls caching
  private final Module[] myAffectedModules;

  public FileSetCompileScope(final VirtualFile[] files, Module[] modules) {
    myAffectedModules = modules;
    ApplicationManager.getApplication().runReadAction(
      new Runnable() {
        public void run() {
          for(int idx = 0; idx < files.length; idx++){
            addFile(files[idx]);
          }
        }
      }
    );
  }

  public Module[] getAffectedModules() {
    return myAffectedModules;
  }

  public VirtualFile[] getFiles(final FileType fileType, boolean inSourceOnly) {
    final Set<VirtualFile> files = new HashSet<VirtualFile>();
    final FileTypeManager typeManager = FileTypeManager.getInstance();
    for (Iterator<VirtualFile> it = myRootFiles.iterator(); it.hasNext();) {
      VirtualFile file = it.next();
      if (!file.isValid()) {
        it.remove();
        continue;
      }
      if (file.isDirectory()) {
        addRecursively(files, file, fileType);
      }
      else {
        if (fileType == null || fileType.equals(typeManager.getFileTypeByFile(file))) {
          files.add(file);
        }
      }
    }
    return files.toArray(new VirtualFile[files.size()]);
  }

  public boolean belongs(String url) {
    //url = CompilerUtil.normalizePath(url, '/');
    if (getUrls().contains(url)) {
      return true;
    }
    for (Iterator<String> it = myDirectoryUrls.iterator(); it.hasNext();) {
      String directoryUrl = it.next();
      if (CompilerUtil.startsWith(url, directoryUrl)) {
        return true;
      }
    }
    return false;
  }

  private Set<String> getUrls() {
    if (myUrls == null) {
      myUrls = new HashSet<String>();
      for (Iterator<VirtualFile> it = myRootFiles.iterator(); it.hasNext();) {
        VirtualFile file = it.next();
        String url = file.getUrl();
        myUrls.add(url);
      }
    }
    return myUrls;
  }

  private void addFile(VirtualFile file) {
    if (file.isDirectory()) {
      myDirectoryUrls.add(file.getUrl() + "/");
    }
    myRootFiles.add(file);
    myUrls = null;
  }

  private void addRecursively(final Collection<VirtualFile> container, final VirtualFile fromDirectory, FileType fileType) {
    VirtualFile[] children = fromDirectory.getChildren();
    if (children.length > 0) {
      final FileTypeManager typeManager = FileTypeManager.getInstance();
      for (int idx = 0; idx < children.length; idx++) {
        VirtualFile child = children[idx];
        if (child.isDirectory()) {
          addRecursively(container, child, fileType);
        }
        else {
          if (fileType == null || fileType.equals(typeManager.getFileTypeByFile(child))) {
            container.add(child);
          }
        }
      }
    }
  }
}
