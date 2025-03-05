// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.newProject;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;


/**
 * @deprecated Use {@link com.jetbrains.python.newProjectWizard}
 */
@Deprecated(forRemoval = true)
public interface PyFrameworkProjectGenerator {

  @NotNull
  @NlsSafe
  String getFrameworkTitle();

  boolean isFrameworkInstalled(@NotNull Sdk sdk);
}
