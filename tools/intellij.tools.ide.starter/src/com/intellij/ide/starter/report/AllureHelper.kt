package com.intellij.ide.starter.report

import com.intellij.ide.starter.telemetry.computeWithSpan
import com.intellij.openapi.diagnostic.logger
import com.intellij.tools.ide.util.common.logError
import io.qameta.allure.Allure
import io.qameta.allure.model.Status
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.Objects
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.name
import kotlin.io.path.walk

object AllureHelper {
  enum class MimeType(val value: String, val ext: String) {
    TEXT("text/plain", ".txt"),
    JSON("text/json", ".json"),
    HTML("text/html", ".html"),
    JAVA("text/x-java-source", ".java"),
    PNG("image/jpeg", ".png"),
    JPEG("image/jpeg", ".jpeg"),
    SVG("image/svg+xml", ".svg"),
    XML("application/xml", ".xml"),
    PDF("application/pdf", ".pdf"),
    SQL("text/x-sql", ".sql"),
    CSV("text/csv", ".csv");

    companion object {
      fun getMimeTypeByExtension(file: File): MimeType {
        return entries.firstOrNull { it.ext == ".${file.extension}" } ?: TEXT
      }
    }
  }

  private val LOG get() = logger<AllureHelper>()

  fun <T> step(name: String, action: () -> T): T {
    LOG.info("Step: $name")
    return Allure.step(name, Allure.ThrowableContextRunnable { action.invoke() })
  }

  fun step(name: String) {
    Allure.step(name)
  }

  fun skippedStep(name: String) {
    Allure.step(name, Status.SKIPPED)
  }

  fun attachFile(name: String, file: File) {
    val mimeType = MimeType.getMimeTypeByExtension(file)
    Allure.getLifecycle().addAttachment(name, mimeType.value, mimeType.ext, file.readBytes())
  }

  fun attachFile(name: String, file: Path) {
    attachFile(name, file.toFile())
  }

  fun attachText(name: String, value: String) {
    attach(name, value, MimeType.TEXT)
  }

  fun attach(name: String, value: Any, type: MimeType) {
    val bytes = if (value is ByteArray) value
    else Objects.toString(value)
      .toByteArray(StandardCharsets.UTF_8)
    Allure.getLifecycle().addAttachment(name, type.value, type.ext, bytes)
  }

  /**
   * Iterates over the files in the given directory and attaches them to the Allure report.
   *
   * @param dir The directory containing the files to be attached.
   */
  @OptIn(ExperimentalPathApi::class)
  fun addAttachmentsFromDir(dir: Path, filter: (Path) -> Boolean = { true }) {
    computeWithSpan("Attach files to Allure report"){ span ->
      span.setAttribute("dir", dir.toString())
      dir.walk()
        .filter(filter)
        .forEach { file ->
          kotlin.runCatching {
            val name = file.parent.fileName.toString() + "/" + file.name
            attachFile(name, file)
          }.onFailure {
            logError("Fail to attach file: ${file}", it)
          }
        }
    }
  }
}