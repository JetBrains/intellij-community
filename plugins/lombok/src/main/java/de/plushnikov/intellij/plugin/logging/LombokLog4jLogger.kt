package de.plushnikov.intellij.plugin.logging

import com.intellij.lang.logging.JvmLogger
import com.siyeh.ig.psiutils.JavaLoggingUtils

class LombokLog4jLogger : JvmLogger by JvmLoggerAnnotationDelegate(
  JavaLoggingUtils.LOG4J,
  "Lombok Log4j",
  LombokLoggingUtils.LOG4J_ANNOTATION,
  400
) {
  override fun toString(): String = id
}