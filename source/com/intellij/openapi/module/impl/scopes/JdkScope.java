/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.module.impl.scopes;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.Arrays;
import java.util.LinkedHashSet;

/**
 * @author max
 */
public class JdkScope extends GlobalSearchScope {
  private LinkedHashSet<VirtualFile> myEntries = new LinkedHashSet<VirtualFile>();
  private String myJdkName;
  private final ProjectFileIndex myIndex;

  public JdkScope(Project project, JdkOrderEntry jdk) {
    myIndex = ProjectRootManager.getInstance(project).getFileIndex();
    myJdkName = jdk.getJdkName();
    myEntries.addAll(Arrays.asList(jdk.getFiles(OrderRootType.CLASSES)));
  }

  public int hashCode() {
    return myJdkName.hashCode();
  }

  public boolean equals(Object object) {
    if (object == this) return true;
    if (object.getClass() != JdkScope.class) return false;

    final JdkScope that = ((JdkScope)object);
    return that.myJdkName.equals(myJdkName);
  }

  public boolean contains(VirtualFile file) {
    return myEntries.contains(getFileRoot(file));
  }

  private VirtualFile getFileRoot(VirtualFile file) {
    if (myIndex.isLibraryClassFile(file)) {
      return myIndex.getClassRootForFile(file);
    }
    if (myIndex.isInContent(file)) {
      return myIndex.getSourceRootForFile(file);
    }
    return null;
  }

  public int compare(VirtualFile file1, VirtualFile file2) {
    final VirtualFile r1 = getFileRoot(file1);
    final VirtualFile r2 = getFileRoot(file2);
    for (VirtualFile root : myEntries) {
      if (r1 == root) return 1;
      if (r2 == root) return -1;
    }
    return 0;
  }

  public boolean isSearchInModuleContent(Module aModule) {
    return false;
  }

  public boolean isSearchInLibraries() {
    return true;
  }
}
