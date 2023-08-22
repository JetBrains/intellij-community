// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.pathValidation

import com.intellij.execution.Platform
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.sdk.appxProduct
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.JComponent
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries

/**
 * To be used with [validateExecutableFile] and [validateEmptyDir]
 * @param path from path field value.
 * @param fieldIsEmpty message to show if field is empty
 * @param component see [ValidationInfo.component]
 * @param platformAndRoot to validate path against
 */
class ValidationRequest(@NonNls internal val path: String?,
                        @Nls val fieldIsEmpty: String = PySdkBundle.message("path.validation.field.empty"),
                        private val platformAndRoot: PlatformAndRoot,
                        private val component: JComponent? = null) {
  internal fun validate(getMessage: (Path) -> @Nls String?): ValidationInfo? {
    val message: @Nls String? = when {
      path.isNullOrEmpty() -> fieldIsEmpty
      !isAbsolutePath(path) -> PySdkBundle.message("path.validation.must.be.absolute")
      path.endsWith(" ") -> PySdkBundle.message("path.validation.ends.with.whitespace")
      else -> platformAndRoot.root?.let {
        try {
          val nioPath = it.resolve(path)
          getMessage(nioPath)
        }
        catch (e: InvalidPathException) {
          PySdkBundle.message("path.validation.invalid", e.message)
        }
        catch (e: IOException) {
          PySdkBundle.message("path.validation.inaccessible", e.message)
        }
      }
    }
    return message?.let { ValidationInfo(it, component) }
  }

  private fun isAbsolutePath(path: String): Boolean = when (platformAndRoot.platform) {
    Platform.UNIX -> path.startsWith("/")
    // On Windows user may create project in \\wsl
    Platform.WINDOWS -> OSAgnosticPathUtil.isAbsoluteDosPath(path) || path.startsWith("\\\\wsl")
  }
}

/**
 * Ensure file is executable
 */
@RequiresBackgroundThread
fun validateExecutableFile(
  request: ValidationRequest
): ValidationInfo? = request.validate {
  if (it.appxProduct != null) return@validate null // Nio can't be used to validate appx, assume file is valid
  when {
    it.isRegularFile() -> if (it.isExecutable()) null else PySdkBundle.message("path.validation.cannot.execute", it)
    it.isDirectory() -> PySdkBundle.message("path.validation.cannot.execute", it)
    else -> PySdkBundle.message("path.validation.file.not.found", it)
  }
}

/**
 * Ensure directory either doesn't exist or empty
 */
@RequiresBackgroundThread
fun validateEmptyDir(request: ValidationRequest,
                     @Nls notADirectory: String,
                     @Nls directoryNotEmpty: String
): ValidationInfo? = request.validate {
  when {
    it.isDirectory() -> if (it.listDirectoryEntries().isEmpty()) null else directoryNotEmpty
    it.isRegularFile() || it.appxProduct != null -> notADirectory
    else -> null
  }
}
