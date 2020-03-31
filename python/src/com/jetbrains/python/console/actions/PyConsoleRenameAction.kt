// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.console.actions

import com.intellij.ide.actions.ToolWindowTabRenameActionBase
import com.intellij.openapi.project.DumbAware
import com.jetbrains.python.console.PythonConsoleToolWindowFactory

class PyConsoleRenameAction : ToolWindowTabRenameActionBase(PythonConsoleToolWindowFactory.ID, "Enter new console name:"),
                              DumbAware