package com.intellij.compiler.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;

import java.util.HashSet;
import java.util.Set;

public class OneProjectItemCompileScope implements CompileScope{
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.impl.OneProjectItemCompileScope");
  private final Project myProject;
  private final VirtualFile myFile;
  private final String myUrl;

  public OneProjectItemCompileScope(Project project, VirtualFile file) {
    myProject = project;
    myFile = file;
    final String url = SystemInfo.isFileSystemCaseSensitive? file.getUrl() : file.getUrl().toLowerCase();
    myUrl = file.isDirectory()? url + "/" : url;
  }

  public VirtualFile[] getFiles(final FileType fileType, final boolean inSourceOnly) {
    final Set<VirtualFile> files = new HashSet<VirtualFile>();
    final FileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    final ContentIterator iterator = new CompilerContentIterator(fileType, projectFileIndex, inSourceOnly, files);
    if (myFile.isDirectory()){
      projectFileIndex.iterateContentUnderDirectory(myFile, iterator);
    }
    else{
      iterator.processFile(myFile);
    }
    return files.toArray(new VirtualFile[files.size()]);
  }

  public boolean belongs(String url) {
    if (!SystemInfo.isFileSystemCaseSensitive) {
      url = url.toLowerCase();
    }
    if (myFile.isDirectory()){
      return url.startsWith(myUrl);
    }
    else{
      return url.equals(myUrl);
    }
  }

  public Module[] getAffectedModules() {
    final Module module = VfsUtil.getModuleForFile(myProject, myFile);
    if (module == null) {
      LOG.assertTrue(false, "Module is null for file " + myFile.getPresentableUrl());
      return Module.EMPTY_ARRAY;
    }
    return new Module[] {module};
  }

}
