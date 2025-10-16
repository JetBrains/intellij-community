package com.intellij.ide.starter.ide.installer

import java.nio.file.Path

open class IdeInstallerFile(val installerFile: Path, val buildNumber: String) {
  companion object
}