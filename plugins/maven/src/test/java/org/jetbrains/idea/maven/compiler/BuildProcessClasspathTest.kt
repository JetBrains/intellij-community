// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.compiler

import com.intellij.compiler.server.impl.BuildProcessClasspathManager
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.psi.impl.light.LightJavaModule
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.File

class BuildProcessClasspathTest : BareTestFixtureTestCase() {
  @Test fun testBuildProcessClasspath() {
    val classpath = BuildProcessClasspathManager(testRootDisposable).getBuildProcessClasspath(DefaultProjectFactory.getInstance().defaultProject)
    val libs = classpath.mapTo(HashSet()) { LightJavaModule.moduleName(File(it).name) }
    assertThat(libs).contains("intellij.maven.jps", "plexus.utils")
  }
}