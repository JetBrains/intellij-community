// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.run;

import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.Nullable;

/**
 * @author Leonid Shalupov
 */
public interface AbstractPythonRunConfigurationParams extends PythonRunParams {
  @Nullable
  Module getModule();
}
