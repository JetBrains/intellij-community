package com.jetbrains.python.configuration;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.io.FileUtil;
import com.jetbrains.python.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
* @author yole
*/
public class VirtualEnvProjectFilter implements Predicate<Sdk> {
  private final String myBasePath;

  public VirtualEnvProjectFilter(@Nullable final String basePath) {
    myBasePath = basePath;
  }

  @Override
  public boolean apply(@Nullable final Sdk input) {
    if (input != null && PythonSdkType.isVirtualEnv(input)) {
      PythonSdkAdditionalData data = (PythonSdkAdditionalData) input.getSdkAdditionalData();
      if (data != null) {
        final String path = data.getAssociatedProjectPath();
        if (path != null && (myBasePath == null || !path.equals(myBasePath))) {
          return true;
        }
      }
    }
    return false;
  }

  public static void removeNotMatching(Project project, List<Sdk> sdks) {
    if (project != null) {
      final String basePath = project.getBasePath();
      if (basePath != null) {
        Iterables.removeIf(sdks, new VirtualEnvProjectFilter(FileUtil.toSystemIndependentName(basePath)));
      }
    }
  }

  public static void removeAllAssociated(List<Sdk> sdks) {
    Iterables.removeIf(sdks, new VirtualEnvProjectFilter(null));
  }
}
