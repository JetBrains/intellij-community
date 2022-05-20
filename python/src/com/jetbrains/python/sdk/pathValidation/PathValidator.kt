// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.pathValidation

import com.intellij.openapi.ui.ValidationInfo
import com.jetbrains.python.PyBundle
import org.jetbrains.annotations.Nls
import java.nio.file.Path
import javax.swing.JComponent

/**
 * Converts target path to [PathInfo] much like [PathInfo.forLocalPath] but each target may provide implementation
 */
typealias PathConverter = (String) -> PathInfo?

/**
 * To be used with [validateExecutableFile] and [validateEmptyDir]
 * [path] is target path. [fieldIsEmpty] is an error message, [pathConverter] is from target (local target assumed if null)
 */
class ValidationRequest(internal val path: String?,
                        @Nls val fieldIsEmpty: String,
                        private val pathConverter: PathConverter? = null,
                        private val component: JComponent? = null) {
  internal fun validate(getMessage: (PathInfo?) -> @Nls String?): ValidationInfo? {
    val message: @Nls String? = when {
      path.isNullOrBlank() -> fieldIsEmpty
      else -> getMessage((pathConverter ?: { PathInfo.forLocalPath(Path.of(path)) })(path))
    }
    return message?.let { ValidationInfo(it, component) }
  }
}

/**
 * Ensure file is executable
 */
fun validateExecutableFile(
  request: ValidationRequest
): ValidationInfo? = request.validate {
  when (it) {
    is PathInfo.RegularFile -> if (it.executable) null else PyBundle.message("python.sdk.cannot.execute", request.path)
    is PathInfo.Directory -> PyBundle.message("python.sdk.cannot.execute", request.path)
    else -> PyBundle.message("python.sdk.file.not.found", request.path)
  }
}

/**
 * Ensure directory either doesn't exist or empty
 */
fun validateEmptyDir(request: ValidationRequest,
                     @Nls notADirectory: String,
                     @Nls directoryNotEmpty: String
): ValidationInfo? = request.validate {
  when (it) {
    is PathInfo.Directory -> if (it.empty) null else directoryNotEmpty
    is PathInfo.RegularFile -> notADirectory
    else -> null
  }
}