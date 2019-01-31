// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.execution.configuration.RunConfigurationExtensionBase;
import com.intellij.openapi.extensions.ExtensionPointName;

/**
 * @author traff
 */
public abstract class PythonRunConfigurationExtension extends RunConfigurationExtensionBase<AbstractPythonRunConfiguration<?>> {
  protected static final ExtensionPointName<PythonRunConfigurationExtension> EP_NAME =
    new ExtensionPointName<>("Pythonid.runConfigurationExtension");
}
