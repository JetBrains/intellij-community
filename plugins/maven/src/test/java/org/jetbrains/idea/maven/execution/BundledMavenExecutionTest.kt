// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution

class BundledMavenExecutionTest : MavenExecutionTest() {

  override fun setUp() {
    super.setUp()
    toggleScriptsRegistryKey(false)
  }
}