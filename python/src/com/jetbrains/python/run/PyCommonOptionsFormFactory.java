// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.openapi.application.ApplicationManager;


public abstract class PyCommonOptionsFormFactory {
  public static PyCommonOptionsFormFactory getInstance() {
    return ApplicationManager.getApplication().getService(PyCommonOptionsFormFactory.class);
  }

  public abstract AbstractPyCommonOptionsForm createForm(PyCommonOptionsFormData data);
}
