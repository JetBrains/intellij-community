/*
 * @author: Eugene Zhuravlev
 * Date: Jan 20, 2003
 * Time: 5:34:19 PM
 */
package com.intellij.compiler.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class ModuleCompileScope extends FileIndexCompileScope {
  private final Project myProject;
  private final Set<Module> myScopeModules;

  public ModuleCompileScope(final Module module, boolean includeDependentModules) {
    myProject = module.getProject();
    myScopeModules = new HashSet<Module>();
    if (includeDependentModules) {
      buildScopeModulesSet(module);
    }
    else {
      myScopeModules.add(module);
    }
  }

  public ModuleCompileScope(Project project, final Module[] modules, boolean includeDependentModules) {
    myProject = project;
    myScopeModules = new HashSet<Module>();
    for (int idx = 0; idx < modules.length; idx++) {
      Module module = modules[idx];
      if (includeDependentModules) {
        buildScopeModulesSet(module);
      }
      else {
        myScopeModules.add(module);
      }
    }
  }

  private void buildScopeModulesSet(Module module) {
    myScopeModules.add(module);
    final Module[] dependencies = ModuleRootManager.getInstance(module).getDependencies();
    for (int idx = 0; idx < dependencies.length; idx++) {
      Module dependency = dependencies[idx];
      if (!myScopeModules.contains(dependency)) { // may be in case of module circular dependencies
        buildScopeModulesSet(dependency);
      }
    }
  }

  public Module[] getAffectedModules() {
    return myScopeModules.toArray(new Module[myScopeModules.size()]);
  }

  protected FileIndex[] getFileIndices() {
    final FileIndex[] indices = new FileIndex[myScopeModules.size()];
    int idx = 0;
    for (Iterator it = myScopeModules.iterator(); it.hasNext();) {
      final Module module = (Module)it.next();
      indices[idx++] = ModuleRootManager.getInstance(module).getFileIndex();
    }
    return indices;
  }

  public boolean belongs(String url) {
    final Module[] modules = ModuleManager.getInstance(myProject).getModules();
    Module candidateModule = null;
    int maxUrlLength = 0;
    final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(myProject).getFileIndex();
    for (int idx = 0; idx < modules.length; idx++) {
      final Module module = modules[idx];
      final String[] contentRootUrls = ModuleRootManager.getInstance(module).getContentRootUrls();
      for (int i = 0; i < contentRootUrls.length; i++) {
        final String contentRootUrl = contentRootUrls[i];
        if (contentRootUrl.length() < maxUrlLength) {
          continue;
        }
        if (!CompilerUtil.startsWith(url, contentRootUrl + "/")) {
          continue;
        }
        if (contentRootUrl.length() == maxUrlLength) {
          if (candidateModule == null) {
            candidateModule = module;
          }
          else {
            // the same content root exists in several modules
            if (!candidateModule.equals(module)) {
              candidateModule = ApplicationManager.getApplication().runReadAction(new Computable<Module>() {
                public Module compute() {
                  final VirtualFile contentRootFile = VirtualFileManager.getInstance().findFileByUrl(contentRootUrl);
                  if (contentRootFile != null) {
                    return projectFileIndex.getModuleForFile(contentRootFile);
                  }
                  return null;
                }
              });
            }
          }
        }
        else {
          maxUrlLength = contentRootUrl.length();
          candidateModule = module;
        }
      }
    }

    if (candidateModule != null && myScopeModules.contains(candidateModule)) {
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(candidateModule);
      final String[] excludeRootUrls = moduleRootManager.getExcludeRootUrls();
      for (int i = 0; i < excludeRootUrls.length; i++) {
        if (CompilerUtil.startsWith(url, excludeRootUrls[i] + "/")) {
          return false;
        }
      }
      final String[] sourceRootUrls = moduleRootManager.getSourceRootUrls();
      for (int i = 0; i < sourceRootUrls.length; i++) {
        if (CompilerUtil.startsWith(url, sourceRootUrls[i] + "/")) {
          return true;
        }
      }
    }

    return false;
  }

}
