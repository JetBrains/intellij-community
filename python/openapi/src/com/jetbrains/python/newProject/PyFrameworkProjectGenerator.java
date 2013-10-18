package com.jetbrains.python.newProject;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.platform.DirectoryProjectGenerator;

/**
 * @author yole
 */
public interface PyFrameworkProjectGenerator<T> extends DirectoryProjectGenerator<T> {
  String getFrameworkTitle();

  boolean isFrameworkInstalled(Project project, Sdk sdk);

  boolean acceptsRemoteSdk();

  boolean supportsPython3();
}
