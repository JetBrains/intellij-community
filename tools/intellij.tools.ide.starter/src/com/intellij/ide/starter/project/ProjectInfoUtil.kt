package com.intellij.ide.starter.project

import java.nio.file.Path

val ProjectInfoSpec.projectDir: Path
  get() = when (this) {
    is LocalProjectInfo -> projectDir
    is ReusableLocalProjectInfo -> projectDir
    else -> error("Project directory for project spec type ${this.javaClass} can't be determined")
  }
