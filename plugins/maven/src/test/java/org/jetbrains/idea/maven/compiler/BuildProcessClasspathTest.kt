// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.compiler

import com.intellij.compiler.server.impl.BuildProcessClasspathManager
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class BuildProcessClasspathTest : BareTestFixtureTestCase() {
  @Test fun testBuildProcessClasspath() = runBlocking {
    val classpath = BuildProcessClasspathManager(testRootDisposable).getBuildProcessClasspath(DefaultProjectFactory.getInstance().defaultProject)
    assertThat(classpath)
      .anyMatch({ it.contains("intellij.maven.jps") }, "Maven-JPS plugin must be on classpath of the compiler process")
      .anyMatch({ it.contains("plexus-utils") }, "Plexus Utils is a dependency of Maven-JPS plugins and must be present on classpath")
    return@runBlocking
  }
}