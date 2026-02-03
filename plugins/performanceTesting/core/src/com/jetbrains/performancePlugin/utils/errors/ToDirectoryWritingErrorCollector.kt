// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.utils.errors

import com.intellij.openapi.diagnostic.ExceptionWithAttachments
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.ExceptionUtil
import com.intellij.util.io.createDirectories
import com.intellij.util.io.outputStream
import com.intellij.util.io.write
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger

class ToDirectoryWritingErrorCollector(
  private val presentableName: String,
  private val directory: Path,
  private val limitOfErrors: Int
) : ErrorCollector {

  private val logger = Logger.getInstance("error-collector-$presentableName")

  private val nextErrorId = AtomicInteger()

  override fun addError(error: Throwable) {
    val errorId = nextErrorId.incrementAndGet()
    if (errorId > limitOfErrors) {
      throw RuntimeException("Too many errors reported for $presentableName")
    }

    val errorHome = directory.resolve("error-$errorId")
    errorHome.createDirectories()
    logger.warn("Error #$errorId has been collected. Details will be saved to $errorHome. Message: ${error.message ?: error.javaClass.name}")
    errorHome.resolve("stacktrace.txt").write(ExceptionUtil.getThrowableText(error))
    if (error is ExceptionWithAttachments) {
      for (attachment in error.attachments) {
        val attachmentPath = errorHome.resolve(attachment.path)
        attachmentPath.outputStream().use { os ->
          attachment.openContentStream().copyTo(os)
        }
      }
    }
  }

  override fun <T> runCatchingError(computation: () -> T): T? =
    runCatching(computation).onFailure { addError(it) }.getOrNull()

  override val numberOfErrors
    get() = nextErrorId.get()
}