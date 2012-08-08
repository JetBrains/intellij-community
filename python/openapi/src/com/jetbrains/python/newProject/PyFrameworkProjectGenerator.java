package com.jetbrains.python.newProject;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.platform.DirectoryProjectGenerator;

/**
 * @author yole
 */
public interface PyFrameworkProjectGenerator<T> extends DirectoryProjectGenerator<T> {
  String getFrameworkTitle();

  boolean isFrameworkInstalled(Sdk sdk);
}
