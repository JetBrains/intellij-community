// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import kotlinx.collections.immutable.plus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JetBrainsProductProperties
import org.jetbrains.intellij.build.TEST_FRAMEWORK_WITH_JAVA_RT
import org.jetbrains.intellij.build.impl.copyDirWithFileFilter
import org.jetbrains.intellij.build.zipSourcesOfModules
import java.nio.file.Path
import java.util.function.Predicate

const val PYDEVD_PACKAGE: String = "pydevd_package"

abstract class PyCharmPropertiesBase(enlargeWelcomeScreen: Boolean) : JetBrainsProductProperties() {
  override val baseFileName: String
    get() = "pycharm"

  init {
    if (enlargeWelcomeScreen) {
      additionalVmOptions += "-Dwelcome.screen.defaultWidth=1000"
      additionalVmOptions += "-Dwelcome.screen.defaultHeight=720"
    }
    reassignAltClickToMultipleCarets = true
    useSplash = true
    productLayout.addPlatformSpec(TEST_FRAMEWORK_WITH_JAVA_RT)
    buildCrossPlatformDistribution = true
    mavenArtifacts.additionalModules = mavenArtifacts.additionalModules.addAll(listOf(
      "intellij.java.compiler.antTasks",
      "intellij.platform.testFramework.common",
      "intellij.platform.testFramework.junit5",
      "intellij.platform.testFramework.teamCity",
      "intellij.platform.testFramework",
    ))
  }

  override suspend fun copyAdditionalFiles(context: BuildContext, targetDir: Path) {
    zipSourcesOfModules(
      modules = listOf("intellij.python.community", "intellij.python.psi"),
      targetFile = Path.of("$targetDir/lib/src/pycharm-openapi-src.zip"),
      includeLibraries = false,
      context = context,
    )

    copyDirWithFileFilter(
      fromDir = getKeymapReferenceDirectory(context),
      targetDir = targetDir.resolve("help"),
      fileFilter = Predicate { it.toString().endsWith(".pdf") }
    )
  }

  open fun getKeymapReferenceDirectory(context: BuildContext): Path = context.paths.projectHome.resolve("python/help")
}
