// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.navigation

import com.intellij.openapi.editor.LogicalPosition
import training.learn.interfaces.Module
import training.learn.lesson.general.navigation.FileStructureLesson

class PythonFileStructureLesson(module: Module) : FileStructureLesson(module, "Python") {
  override val existedFile: String = "src/file_structure_demo.py"
  override val methodToFindPosition: LogicalPosition = LogicalPosition(73, 8)
}
