// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.remote.PathMappingProvider;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author traff
 */
public class PyRemoteAnalyzeStacktraceFilter extends PythonTracebackFilter {
  public PyRemoteAnalyzeStacktraceFilter(Project project) {
    super(project);
  }

  @Override
  @Nullable
  protected VirtualFile findFileByName(@NotNull String fileName) {
    VirtualFile vFile = super.findFileByName(fileName);
    if (vFile != null) {
      return vFile;
    }
    for (Module m: ModuleManager.getInstance(getProject()).getModules()) {
      Sdk s = PythonSdkType.findPythonSdk(m);
      if (PySdkUtil.isRemote(s)) {
        PyRemoteSdkAdditionalDataBase data = (PyRemoteSdkAdditionalDataBase) s.getSdkAdditionalData();

        if (data != null) {
          for (PathMappingProvider provider: PathMappingProvider.getSuitableMappingProviders(data)) {
            PathMappingSettings mappingSettings = provider.getPathMappingSettings(getProject(), data);
            String localFile = mappingSettings.convertToLocal(fileName);
            VirtualFile file = LocalFileSystem.getInstance().findFileByPath(localFile);
            if (file != null && file.exists()) {
              return file;
            }
          }

        }
      }
    }

    return null;
  }
}
