package com.intellij.ide.starter.junit5.config

import com.intellij.ide.starter.ci.CIServer
import com.intellij.ide.starter.config.ConfigurationStorage
import com.intellij.ide.starter.config.useLastDownloadedBuild
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * Signals to use a locally available build instead of downloading one.
 */
open class UseLatestDownloadedIdeBuild : BeforeAllCallback, BeforeEachCallback {
  private fun configure() {
    require(!CIServer.instance.isBuildRunningOnCI) {
      "${ConfigurationStorage.useLastDownloadedBuild()} should not be used on CI. Downloaded build may not correspond with the changes"
    }
    ConfigurationStorage.useLastDownloadedBuild(true)
  }

  override fun beforeEach(context: ExtensionContext) = configure()

  override fun beforeAll(context: ExtensionContext?) = configure()
}

