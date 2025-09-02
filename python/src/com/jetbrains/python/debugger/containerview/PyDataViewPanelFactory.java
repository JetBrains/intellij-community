// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger.containerview;

import com.intellij.openapi.extensions.ExtensionPointName;

public abstract class PyDataViewPanelFactory {
  public static final ExtensionPointName<PyDataViewPanelFactory> EP_NAME = ExtensionPointName.create("Pythonid.dataViewPanelFactory");

  public abstract PyDataViewerAbstractPanel createDataViewerPanel(PyDataViewerModel state);
}
