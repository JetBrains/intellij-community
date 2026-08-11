// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal.addPyProject

/**
 * Action to show [AddPyProjectDialog], ask user for a project name, and create a new pyproject
 */
internal class AddPyProjectAction : PyProjectActionImpl(forNewProject = true)