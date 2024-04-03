package de.plushnikov.intellij.plugin.logging

object LombokLoggingUtils {
  const val SLF4J_ANNOTATION = "lombok.extern.slf4j.Slf4j"
  const val LOG4J2_ANNOTATION = "lombok.extern.log4j.Log4j2"
  const val LOG4J_ANNOTATION = "lombok.extern.log4j.Log4j"
  const val COMMONS_ANNOTATION = "lombok.extern.apachecommons.CommonsLog"

  const val ID_LOMBOK_APACHE_COMMONS_LOGGING = "Lombok Apache Commons Logging"
  const val ID_LOMBOK_SLF_4_J = "Lombok Slf4j"
  const val ID_LOMBOK_LOG_4_J_2 = "Lombok Log4j2"
  const val ID_LOMBOK_LOG_4_J = "Lombok Log4j"
}