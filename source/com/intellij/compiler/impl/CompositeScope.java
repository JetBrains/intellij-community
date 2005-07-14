/*
 * @author: Eugene Zhuravlev
 * Date: Feb 5, 2003
 * Time: 4:17:58 PM
 */
package com.intellij.compiler.impl;

import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;

import java.util.*;

public class CompositeScope extends UserDataHolderBase implements CompileScope{
  private final List<CompileScope> myScopes = new ArrayList<CompileScope>();

  public CompositeScope(CompileScope scope1, CompileScope scope2) {
    myScopes.add(scope1);
    myScopes.add(scope2);
  }

  public CompositeScope(CompileScope[] scopes) {
    myScopes.addAll(Arrays.asList(scopes));
  }

  public VirtualFile[] getFiles(FileType fileType, boolean inSourceOnly) {
    List<VirtualFile> allFiles = new ArrayList<VirtualFile>();
    for (Iterator it = myScopes.iterator(); it.hasNext();) {
      CompileScope scope = (CompileScope)it.next();
      final VirtualFile[] files = scope.getFiles(fileType, inSourceOnly);
      if (files.length > 0) {
        allFiles.addAll(Arrays.asList(files));
      }
    }
    return allFiles.toArray(new VirtualFile[allFiles.size()]);
  }

  public boolean belongs(String url) {
    for (Iterator<CompileScope> it = myScopes.iterator(); it.hasNext();) {
      CompileScope scope = it.next();
      if (scope.belongs(url)) {
        return true;
      }
    }
    return false;
  }

  public Module[] getAffectedModules() {
    Set<Module> modules = new HashSet<Module>();
    for (Iterator<CompileScope> it = myScopes.iterator(); it.hasNext();) {
      final CompileScope compileScope = it.next();
      modules.addAll(Arrays.asList(compileScope.getAffectedModules()));
    }
    return modules.toArray(new Module[modules.size()]);
  }

  public <T> T getUserData(Key<T> key) {
    for (Iterator<CompileScope> it = myScopes.iterator(); it.hasNext();) {
      CompileScope compileScope = it.next();
      T userData = compileScope.getUserData(key);
      if (userData != null) {
        return userData;
      }
    }
    return super.getUserData(key);
  }
}
