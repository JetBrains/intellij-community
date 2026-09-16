package com.intellij.python.lsp.core

import com.intellij.python.pytools.backend.PyTool
import com.jetbrains.python.packaging.common.PythonPackage
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.management.getInstalledPackageSnapshot

fun PythonPackageManager.getInstalledToolPackage(pyTool: PyTool<*>): PythonPackage? {
  return getInstalledPackageSnapshot(pyTool.packageName.name)
}
