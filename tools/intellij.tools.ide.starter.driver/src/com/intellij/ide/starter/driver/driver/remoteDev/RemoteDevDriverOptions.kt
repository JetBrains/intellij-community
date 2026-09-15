package com.intellij.ide.starter.driver.driver.remoteDev

import com.intellij.ide.starter.driver.engine.DriverOptions
import com.intellij.ide.starter.utils.PortUtil.getAvailablePort


class RemoteDevDriverOptions {

  val backendOptions: DriverOptions by lazy { DriverOptions(additionalProperties = remoteDevVmOptions) }
  val backendDebugPort: Int by lazy { getAvailablePort(proposedPort = 5020) }

  val frontendOptions: DriverOptions by lazy {
    DriverOptions(port = getAvailablePort(proposedPort = 8889),
                  rmiPort = getAvailablePort(proposedPort = 11500),
                  webServerPort = getAvailablePort(proposedPort = 7778),
                  additionalProperties = mapOf("rdct.tests.backendJmxPort" to backendOptions.port.toString(),
                                               "rdct.tests.backendJmxHost" to backendOptions.host.hostAddress)
                                         + remoteDevVmOptions)
  }

  val debugPort: Int by lazy { getAvailablePort(proposedPort = 5010) }

  /**
   * It will be added to both frontend and backend VM options
   */
  private val remoteDevVmOptions: Map<String, String> =
    mapOf(
      "ide.mac.file.chooser.native" to "false",
      "idea.welcome.screen.non.modal.enabled" to "false"
    )
}