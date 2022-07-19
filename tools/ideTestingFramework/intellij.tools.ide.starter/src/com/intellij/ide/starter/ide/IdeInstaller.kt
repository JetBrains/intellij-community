// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.starter.ide

import java.nio.file.Path

open class IdeInstaller(val installerFile: Path, val buildNumber: String) {
  companion object
}