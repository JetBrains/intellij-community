// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public final class PyDirectoryIndexExcludePolicy implements DirectoryIndexExcludePolicy {
  private final static String[] SITE_PACKAGES = new String[]{PyNames.SITE_PACKAGES, PyNames.DIST_PACKAGES};

  private final Project myProject;

  public PyDirectoryIndexExcludePolicy(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public String @NotNull [] getExcludeUrlsForProject() {
    List<String> result = new ArrayList<>();
    for (VirtualFile root : ProjectRootManager.getInstance(myProject).getContentRoots()) {
      VirtualFile file = root.findChild(".tox");
      if (file != null) {
        result.add(file.getUrl());
      }
    }

    return ArrayUtilRt.toStringArray(result);
  }

  @Nullable
  @Override
  public Function<Sdk, List<VirtualFile>> getExcludeSdkRootsStrategy() {
    return sdk -> {
      List<VirtualFile> result = new LinkedList<>();

      if (sdk != null) {
        Set<VirtualFile> roots = ContainerUtil.newHashSet(sdk.getRootProvider().getFiles(OrderRootType.CLASSES));

        for (VirtualFile dir : sdk.getRootProvider().getFiles(OrderRootType.CLASSES)) {
          for (String name : SITE_PACKAGES) {
            VirtualFile sitePackages = dir.findChild(name);
            if (sitePackages != null && !roots.contains(sitePackages)) {
              result.add(sitePackages);
            }
          }
        }
      }

      return result;
    };
  }
}
