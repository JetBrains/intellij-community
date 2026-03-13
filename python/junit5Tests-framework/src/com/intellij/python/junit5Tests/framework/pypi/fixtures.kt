// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.framework.pypi

import com.jetbrains.python.packaging.PyPackage
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

/**
 * JUnit5 fixture that starts a [MockPyPIServer] serving the given [packages] and stops it on tear-down.
 *
 * @param packages packages to serve (each gets a minimal wheel generated automatically)
 * @param credentials if provided, the server requires HTTP Basic authentication
 * @param wheelDir directory for generated wheel files; defaults to a temporary directory
 */
@TestOnly
fun mockPyPIServerFixture(
  vararg packages: PyPackage,
  credentials: MockPyPICredentials? = null,
  wheelDir: TestFixture<Path> = tempPathFixture(),
): TestFixture<MockPyPIServer> = testFixture {
  val dir = wheelDir.init()
  val server = MockPyPIServer(dir, packages.toList(), credentials)
  initialized(server) {
    server.stop()
  }
}
