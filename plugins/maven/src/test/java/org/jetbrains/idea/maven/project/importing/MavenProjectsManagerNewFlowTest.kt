// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.project.importing

import com.intellij.openapi.util.registry.Registry
import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import org.junit.Assume
import org.junit.Test

class MavenProjectsManagerNewFlowTest : MavenMultiVersionImportingTestCase() {
  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    Assume.assumeTrue(Registry.`is`("maven.new.import"))
  }

  @Test
  fun shouldSetMavenFilesIntoProjectManager() {
    val file = createProjectPom("<groupId>test</groupId>" +
                       "<artifactId>project</artifactId>" +
                       "<version>1</version>")
    importViaNewFlow(listOf(file), true, emptyList())

  }
}