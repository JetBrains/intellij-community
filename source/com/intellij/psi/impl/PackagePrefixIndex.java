/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.impl;

import com.intellij.ProjectTopics;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.SmartList;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public class PackagePrefixIndex {
  private static final Object LOCK = new Object();
  private MultiMap<String, Module> myMap;
  private final Project myProject;

  public PackagePrefixIndex(Project project) {
    myProject = project;
    project.getMessageBus().connect(project).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
      public void beforeRootsChange(final ModuleRootEvent event) {
      }

      public void rootsChanged(final ModuleRootEvent event) {
        synchronized (LOCK) {
          myMap = null;
        }
      }
    });
  }

  public Collection<String> getAllPackagePrefixes(@Nullable GlobalSearchScope scope) {
    MultiMap<String, Module> map = myMap;
    if (map != null) {
      return getAllPackagePrefixes(scope, map);
    }

    synchronized (LOCK) {
      if (myMap == null) {
        map = new MultiMap<String, Module>();
        for (final Module module : ModuleManager.getInstance(myProject).getModules()) {
          for (final ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
            for (final SourceFolder folder : entry.getSourceFolders()) {
              final String prefix = folder.getPackagePrefix();
              if (StringUtil.isNotEmpty(prefix)) {
                map.putValue(prefix, module);
              }
            }
          }
        }
        myMap = map;
      }
      return getAllPackagePrefixes(scope, myMap);
    }
  }

  private static Collection<String> getAllPackagePrefixes(final GlobalSearchScope scope, final MultiMap<String, Module> map) {
    if (scope == null) return map.keySet();

    List<String> result = new SmartList<String>();
    for (final String prefix : map.keySet()) {
      modules: for (final Module module : map.get(prefix)) {
        if (scope.isSearchInModuleContent(module)) {
          result.add(prefix);
          break modules;
        }
      }
    }
    return result;
  }
}
