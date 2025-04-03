// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;


public interface PyBreakpointType {
  boolean isBreakpointTypeAllowedInDocument(Project project, Document document);
}
