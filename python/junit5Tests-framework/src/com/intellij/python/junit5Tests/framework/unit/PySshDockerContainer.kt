package com.intellij.python.junit5Tests.framework.unit

import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/**
 * Docker container with python and ssh. Login with `user:123@127.0.0.1:${sshContainer.sshPort}`
 * ```kotlin
 * @Testcontainers
 * class PySshDockerTest {
 *   companion object {
 *     @Container
 *     @JvmStatic
 *     internal val sshContainer = PySshDockerContainer()
 *   }
 *   @Test
 *   fun testContainer() {
 *   sshContainer.sshPort // 22 ->
 * }
 *```
 */
class PySshDockerContainer : GenericContainer<PySshDockerContainer>(
  DockerImageName.parse("registry.jetbrains.team/p/ssh-docker-image-with-python/containers/python_ssh:latest")) {
  init {
    addExposedPort(22)
  }

  /**
   * Port to connect SSH client to
   */
  val sshPort: UShort get() = firstMappedPort.toUShort()
}