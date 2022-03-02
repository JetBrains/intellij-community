// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.navigation

import training.dsl.LessonContext
import training.learn.lesson.general.navigation.RecentFilesLesson

class PythonRecentFilesLesson : RecentFilesLesson() {
  override val sampleFilePath: String = "src/recent_files_demo.py"

  override val transitionMethodName: String = "sorted"
  override val transitionFileName: String = "builtin"
  override val stringForRecentFilesSearch: String = transitionMethodName

  override fun LessonContext.setInitialPosition() = caret(transitionMethodName)
}