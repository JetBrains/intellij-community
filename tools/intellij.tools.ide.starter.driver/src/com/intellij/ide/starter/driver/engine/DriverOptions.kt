package com.intellij.ide.starter.driver.engine

import com.intellij.ide.starter.utils.PortUtil.getAvailablePort
import java.net.InetAddress

class DriverOptions(
  host: InetAddress = InetAddress.getLoopbackAddress(),
  internal val port: Int = getAvailablePort(proposedPort = 7777),
  webServerPort: Int = getAvailablePort(proposedPort = 63000),
  additionalProperties: Map<String, String> = emptyMap()
) {

  val address: String = "${host.hostAddress}:$port"

  val systemProperties: Map<String, String> =
    // https://docs.oracle.com/javase/8/docs/technotes/guides/serialization/filters/serialization-filtering.html
    mapOf(
      // the host name string that should be associated with remote stubs for locally created remote objects, in order to allow clients to invoke methods on the remote object,
      // if not specified: all interfaces the local host (127.0.0.1)
      "java.rmi.server.hostname" to host.hostAddress,
      // the bind address for the default JMX agent,
      // if not specified: all interfaces (0.0.0.0)
      "com.sun.management.jmxremote.host" to host.hostAddress,
      "com.sun.management.jmxremote" to "true",
      "com.sun.management.jmxremote.port" to port.toString(),
      "com.sun.management.jmxremote.authenticate" to "false",
      "com.sun.management.jmxremote.ssl" to "false",
      "com.sun.management.jmxremote.serial.filter.pattern" to "'java.**;javax.**;com.intellij.driver.model.**'",
      "expose.ui.hierarchy.url" to "true",
      "platform.experiment.ab.manual.option" to "control.option",
      "rpc.port" to webServerPort.toString()
    ) + additionalProperties
}