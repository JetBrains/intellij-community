package com.intellij.ide.starter

object Const {
  private const val ENABLE_CLASS_FILE_VERIFICATION_ENV = "ENABLE_CLASS_FILE_VERIFICATION"

  val isClassFileVerificationEnabled = System.getenv(ENABLE_CLASS_FILE_VERIFICATION_ENV).toBoolean()
}
