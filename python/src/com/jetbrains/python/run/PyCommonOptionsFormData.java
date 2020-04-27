// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.run;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

import java.util.List;

public interface PyCommonOptionsFormData {
  Project getProject();
  List<Module> getValidModules();
  boolean showConfigureInterpretersLink();
}
