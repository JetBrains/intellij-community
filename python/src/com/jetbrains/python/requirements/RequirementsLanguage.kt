package com.jetbrains.python.requirements

import com.intellij.lang.Language

class RequirementsLanguage private constructor() : Language("Requirements") {
  companion object {
    val INSTANCE: RequirementsLanguage = RequirementsLanguage()
  }
}