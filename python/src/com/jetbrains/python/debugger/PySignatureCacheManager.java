package com.jetbrains.python.debugger;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

/**
 * @author traff
 */
public abstract class PySignatureCacheManager {


  public static PySignatureCacheManager getInstance(Project project) {
    return ServiceManager.getService(project, PySignatureCacheManager.class);
  }

  public abstract void recordSignature(PySignature signature);
}
