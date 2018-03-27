/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.jetbrains.extensions.python

import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.jetbrains.python.PyNames

@JvmOverloads
fun FileChooserDescriptor.withPythonFiles(allowFilesWithNoExtension: Boolean = false) =
  this.withFileFilter { it.name.endsWith(PyNames.DOT_PY) || (allowFilesWithNoExtension && it.extension == null) }!!
