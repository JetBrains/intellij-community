// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProjectWizard

import com.intellij.openapi.ui.validation.CHECK_NON_EMPTY
import com.intellij.openapi.ui.validation.CHECK_NO_RESERVED_WORDS
import com.intellij.openapi.ui.validation.CHECK_NO_WHITESPACES
import com.intellij.openapi.util.NlsSafe
import com.jetbrains.python.PyBundle
import com.jetbrains.python.Result
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Returns either error or [Path] for project path
 */
fun validateProjectPathAndGetPath(baseDirPath: String): Result<Path, @NlsSafe String> {
  val path = try {
    Paths.get(baseDirPath)
  }
  catch (e: InvalidPathException) {
    return Result.Failure(e.reason)
  }

  if (!path.isAbsolute) {
    return Result.Failure(PyBundle.message("python.sdk.new.error.no.absolute"))
  }

  for (validator in arrayOf(CHECK_NON_EMPTY, CHECK_NO_WHITESPACES, CHECK_NO_RESERVED_WORDS)) {
    validator.curry { baseDirPath }.validate()?.let {
      return Result.Failure(it.message)
    }
  }
  return Result.Success(path)
}