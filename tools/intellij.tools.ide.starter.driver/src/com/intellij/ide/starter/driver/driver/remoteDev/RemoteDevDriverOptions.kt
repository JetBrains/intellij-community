package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.ide.starter.driver.engine.DriverOptions
import com.intellij.ide.starter.utils.PortUtil.getAvailablePort
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes


class RemoteDevDriverOptions {
  val runTimeout: Duration = 10.minutes

  val backendOptions: DriverOptions by lazy { DriverOptions() }
  val backendDebugPort: Int by lazy { getAvailablePort(proposedPort = 5020) }

  val frontendOptions: DriverOptions by lazy {
    DriverOptions(port = getAvailablePort(proposedPort = 8889),
                  webServerPort = getAvailablePort(proposedPort = 7778),
                  additionalProperties = mapOf("rdct.tests.backendJmxPort" to backendOptions.port.toString()))
  }

  val debugPort: Int by lazy { getAvailablePort(proposedPort = 5010) }

  /**
   * It will be added to both frontend and backend VM options
   */
  val remoteDevVmOptions: Map<String, String> =
    mapOf(
      "ide.mac.file.chooser.native" to "false",
      "apple.laf.useScreenMenuBar" to "false",
      "jbScreenMenuBar.enabled" to "false",
    )
}