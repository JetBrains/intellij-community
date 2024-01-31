package de.plushnikov.intellij.plugin.logging

import com.intellij.logging.JvmLogger
import com.siyeh.ig.psiutils.JavaLoggingUtils

class LombokSlf4jLogger : JvmLogger by JvmLoggerAnnotationDelegate(
  JavaLoggingUtils.SLF4J,
  LombokLoggingUtils.SLF4J_ANNOTATION,
  90
) {
  override fun toString(): String = "Lombok SLf4j"
}