package com.intellij.python.junit5Tests.unit.showCase

import com.intellij.python.junit5Tests.framework.unit.PySshDockerContainer
import org.apache.sshd.client.SshClient
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.net.InetSocketAddress

@Testcontainers
class PySshDockerTest {
  companion object {
    // Declare container
    @Container
    @JvmStatic
    internal val sshContainer = PySshDockerContainer()
  }

  @Test
  fun testContainer() {
    val client = SshClient.setUpDefaultClient()
    client.start()
    try {
      val sessionFuture = client.connect("user", InetSocketAddress("127.0.0.1", sshContainer.sshPort.toInt()))
      Assertions.assertTrue(sessionFuture.await(10_000), "Failed to connect to ${sshContainer.sshPort}")
      val session = sessionFuture.session
      session.addPasswordIdentity("123")
      Assertions.assertTrue(session.auth().verify().isSuccess, "failed to authenticate")
      Assertions.assertTrue(session.executeRemoteCommand("uname").trim().isNotBlank())
      session.disconnect(0, "..")
    }
    finally {
      client.stop()
    }
  }
}