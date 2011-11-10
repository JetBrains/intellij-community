package com.jetbrains.python.run;

import com.intellij.openapi.components.ServiceManager;

/**
 * @author yole
 */
public abstract class PyCommonOptionsFormFactory {
  public static PyCommonOptionsFormFactory getInstance() {
    return ServiceManager.getService(PyCommonOptionsFormFactory.class);
  }

  public abstract AbstractPyCommonOptionsForm createForm(PyCommonOptionsFormData data);
}
