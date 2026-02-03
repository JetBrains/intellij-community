package de.plushnikov.intellij.plugin.logging

import com.intellij.lang.logging.JvmLogger
import com.siyeh.ig.psiutils.JavaLoggingUtils


class LombokLog4j2Logger : JvmLogger by JvmLoggerAnnotationDelegate(
  JavaLoggingUtils.LOG4J2,
  LombokLoggingUtils.ID_LOMBOK_LOG_4_J_2,
  LombokLoggingUtils.LOG4J2_ANNOTATION,
  600
) {
  override fun toString(): String = id
}