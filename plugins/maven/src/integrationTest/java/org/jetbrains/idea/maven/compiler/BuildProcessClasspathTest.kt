// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.compiler

import com.intellij.compiler.server.impl.BuildProcessClasspathManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

@TestApplication
class BuildProcessClasspathTest {
  @Test
  fun testBuildProcessClasspath(@TestDisposable disposable: Disposable) = timeoutRunBlocking(30.seconds) {
    val classpath = BuildProcessClasspathManager(disposable).getBuildProcessClasspath(DefaultProjectFactory.getInstance().defaultProject)
    assertThat(classpath)
      .anyMatch({ it.contains("intellij.maven.jps") }, "Maven-JPS plugin must be on classpath of the compiler process")
      .anyMatch({ it.contains("plexus-utils") }, "Plexus Utils is a dependency of Maven-JPS plugins and must be present on classpath")
    return@timeoutRunBlocking
  }
}
