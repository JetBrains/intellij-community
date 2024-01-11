// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.lang.Language

class RequirementsLanguage private constructor() : Language("Requirements") {
  companion object {
    val INSTANCE: RequirementsLanguage = RequirementsLanguage()
  }
}