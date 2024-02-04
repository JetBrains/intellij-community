package de.plushnikov.intellij.plugin.logging

import com.intellij.lang.logging.JvmLogger
import com.siyeh.ig.psiutils.JavaLoggingUtils

class LombokApacheCommonsLogger : JvmLogger by JvmLoggerAnnotationDelegate(
  JavaLoggingUtils.COMMONS_LOGGING,
  LombokLoggingUtils.COMMONS_ANNOTATION,
  500
) {
  override fun toString(): String = "Lombok Apache Commons Logging"
}