package com.intellij.ide.starter.junit5.config

import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.classFileVerification
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

open class EnableClassFileVerification : BeforeEachCallback {
  override fun beforeEach(context: ExtensionContext) {
    ConfigurationStorage.classFileVerification(true)
  }
}

