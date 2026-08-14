package com.jetbrains.python.conda

import com.intellij.platform.eel.provider.localEel
import com.intellij.python.community.impl.conda.CondaPyTool
import com.intellij.python.pytools.getCustomExecutablePath
import com.intellij.python.pytools.setCustomExecutablePath
import java.nio.file.Path

fun saveLocalPythonCondaPath(condaPath: Path?): Unit =
  CondaPyTool.getInstance().setCustomExecutablePath(localEel.descriptor, condaPath)

fun loadLocalPythonCondaPath(): Path? = CondaPyTool.getInstance().getCustomExecutablePath(localEel.descriptor)
