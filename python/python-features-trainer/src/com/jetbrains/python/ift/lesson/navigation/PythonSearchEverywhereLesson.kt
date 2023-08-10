// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ift.lesson.navigation

import training.learn.lesson.general.navigation.SearchEverywhereLesson

class PythonSearchEverywhereLesson : SearchEverywhereLesson() {
  override val sampleFilePath = "src/declaration_and_usages_demo.py"
  override val resultFileName: String = "quadratic_equations_solver.py"
}
