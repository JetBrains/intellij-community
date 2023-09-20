// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project.importing

import com.intellij.maven.testFramework.MavenMultiVersionImportingTestCase
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.use
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.project.MavenProjectsManager
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class MavenProjectsManagerInitializationTest : MavenMultiVersionImportingTestCase() {
  @Test
  fun testAddingManagedFilesFiresActivationEvent() = runBlocking {
    val m1 = createModulePom("m1",
                             """
                             <groupId>test</groupId>
                             <artifactId>m1</artifactId>
                             <version>1</version>
                             """.trimIndent())
    val activated = AtomicBoolean(false)
    Disposer.newDisposable().use { disposable ->
      projectsManager.addManagerListener(object : MavenProjectsManager.Listener {
        override fun activated() {
          activated.set(true)
        }
      }, disposable)
      projectsManager.addManagedFiles(listOf(m1))
    }
    assertTrue("activated() wasn't called on listener", activated.get())
  }
}
