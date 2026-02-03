// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.execService.impl

import java.nio.file.Path


internal sealed interface Arg {
  data class StringArg(val arg: String) : Arg
  data class FileArg(val file: Path, val generator: com.intellij.python.community.execService.FileArgGenerator) : Arg
}