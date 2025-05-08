// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v1

import com.intellij.ide.util.PropertiesComponent
import java.nio.file.Path
import kotlin.io.path.pathString

private const val PYCHARM_CONDA_FULL_LOCAL_PATH = "PYCHARM_CONDA_FULL_LOCAL_PATH"


fun saveLocalPythonCondaPath(condaPath: Path?) {
  PropertiesComponent.getInstance().setValue(PYCHARM_CONDA_FULL_LOCAL_PATH, condaPath?.pathString)
}

fun loadLocalPythonCondaPath(): Path? = PropertiesComponent.getInstance().getValue(PYCHARM_CONDA_FULL_LOCAL_PATH)?.let {
  Path.of(it)
}