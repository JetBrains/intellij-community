package com.intellij.python.pytools.ui

import com.intellij.python.pytools.PyTool
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.getInstalledPackageSnapshot

fun PythonPackageManager.getInstalledToolPackage(pyTool: PyTool): PythonPackage? {
  return pyTool.aliases.firstNotNullOfOrNull { getInstalledPackageSnapshot(it.name) }
}

