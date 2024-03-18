// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.extensions

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.jetbrains.python.PyNames

@JvmOverloads
fun FileChooserDescriptor.withPythonFiles(allowFilesWithNoExtension: Boolean = false): FileChooserDescriptor =
  this.withFileFilter { it.name.endsWith(PyNames.DOT_PY) || (allowFilesWithNoExtension && it.extension == null) }!!
