// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.compiler

import com.intellij.compiler.server.impl.BuildProcessClasspathManager
import com.intellij.testFramework.IdeaTestCase
import org.jetbrains.jps.cmdline.ClasspathBootstrap
import java.io.File

class BuildProcessClasspathTest : IdeaTestCase() {
  fun testBuildProcessClasspath() {
    val cp = mutableListOf<String>()
    cp.addAll(ClasspathBootstrap.getBuildProcessApplicationClasspath())
    cp.addAll(BuildProcessClasspathManager().getBuildProcessPluginsClasspath(project))
    val cpFileNames = cp.map { File(it).name }
    assertContainsElements(cpFileNames,
                           "intellij.maven.jps",
                           "plexus-utils-3.0.22.jar")
  }
}
