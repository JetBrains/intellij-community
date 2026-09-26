// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.conda

import com.intellij.platform.eel.EelDescriptor
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.python.community.impl.conda.CondaPyTool
import com.intellij.python.pytools.backend.getCustomExecutablePath
import com.intellij.python.pytools.backend.setCustomExecutablePath
import java.nio.file.Path

fun savePythonCondaPath(condaPath: Path): Unit = CondaPyTool.getInstance().setCustomExecutablePath(condaPath.getEelDescriptor(), condaPath)

fun loadPythonCondaPath(eelDescriptor: EelDescriptor): Path? = CondaPyTool.getInstance().getCustomExecutablePath(eelDescriptor)
