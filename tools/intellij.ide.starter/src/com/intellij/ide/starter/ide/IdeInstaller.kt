package com.intellij.ide.starter.ide

import java.nio.file.Path

open class IdeInstaller(val installerFile: Path, val buildNumber: String) {
  companion object
}