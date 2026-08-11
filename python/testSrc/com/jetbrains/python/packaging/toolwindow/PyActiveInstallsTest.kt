// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow

import com.intellij.idea.TestFor
import com.intellij.openapi.util.UserDataHolderBase
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@Subsystems.Packaging
@Layers.Functional
internal class PyActiveInstallsTest {

  @Test
  fun `package key is case-insensitive so the same package collapses to one entry`() {
    assertEquals(PyActiveInstalls.packageKey("numpy"), PyActiveInstalls.packageKey("NumPy"))
  }

  @Test
  fun `mark then unmark toggles installing state, normalized by package name`() {
    val installs = PyActiveInstalls()
    assertFalse(installs.isPackageInstalling("numpy"))

    assertTrue(installs.mark(PyActiveInstalls.packageKey("numpy")))
    // A differently-cased spelling of the same package is recognized as installing.
    assertTrue(installs.isPackageInstalling("NumPy"))

    assertTrue(installs.unmark(PyActiveInstalls.packageKey("numpy")))
    assertFalse(installs.isPackageInstalling("numpy"))
  }

  @Test
  fun `mark rejects a duplicate so a second click cannot start a parallel install`() {
    val installs = PyActiveInstalls()
    val key = PyActiveInstalls.packageKey("numpy")
    assertTrue(installs.mark(key))
    assertFalse(installs.mark(key))
  }

  @Test
  fun `namespaced dialog keys are independent of package keys`() {
    val installs = PyActiveInstalls()
    installs.mark("location:https://example.com/pkg.whl")
    assertTrue(installs.isInstalling("location:https://example.com/pkg.whl"))
    assertFalse(installs.isPackageInstalling("pkg"))
  }

  @Test
  fun `state is per-SDK - the same holder shares one instance, different holders are isolated`() {
    val sdkA = UserDataHolderBase()
    val sdkB = UserDataHolderBase()

    // Same holder always resolves to the same instance (stored in its user data).
    assertSame(PyActiveInstalls.of(sdkA), PyActiveInstalls.of(sdkA))

    PyActiveInstalls.of(sdkA).mark(PyActiveInstalls.packageKey("numpy"))

    // Installing numpy into sdkA must not read as installing into sdkB.
    assertTrue(PyActiveInstalls.of(sdkA).isPackageInstalling("numpy"))
    assertFalse(PyActiveInstalls.of(sdkB).isPackageInstalling("numpy"))
  }

  @Test
  @TestFor(issues = ["PY-91529"])
  fun `mark keeps the trace uuid so the running command can be located`() {
    val installs = PyActiveInstalls()
    val key = PyActiveInstalls.packageKey("numpy")

    assertTrue(installs.mark(key, "trace-1"))
    assertEquals("trace-1", installs.traceUuid(key))
  }

  @Test
  @TestFor(issues = ["PY-91529"])
  fun `an install started without a trace has no uuid but still counts as installing`() {
    val installs = PyActiveInstalls()
    val key = PyActiveInstalls.packageKey("numpy")

    assertTrue(installs.mark(key))
    assertTrue(installs.isInstalling(key))
    assertNull(installs.traceUuid(key))
  }

  @Test
  @TestFor(issues = ["PY-91529"])
  fun `unmark clears the uuid so it cannot leak into the next install of the same package`() {
    val installs = PyActiveInstalls()
    val key = PyActiveInstalls.packageKey("numpy")

    installs.mark(key, "trace-1")
    assertTrue(installs.unmark(key))
    assertNull(installs.traceUuid(key))

    // Re-installing the same package without a trace must not resurrect the previous run's uuid.
    installs.mark(key)
    assertNull(installs.traceUuid(key))
  }

  @Test
  @TestFor(issues = ["PY-91529"])
  fun `a rejected duplicate keeps the uuid of the install that is actually running`() {
    val installs = PyActiveInstalls()
    val key = PyActiveInstalls.packageKey("numpy")

    assertTrue(installs.mark(key, "trace-1"))
    assertFalse(installs.mark(key, "trace-2"))
    assertEquals("trace-1", installs.traceUuid(key))
  }

  @Test
  @TestFor(issues = ["PY-91529"])
  fun `traceUuid of a key that is not installing is null`() {
    val installs = PyActiveInstalls()

    assertNull(installs.traceUuid(PyActiveInstalls.packageKey("numpy")))
  }
}
