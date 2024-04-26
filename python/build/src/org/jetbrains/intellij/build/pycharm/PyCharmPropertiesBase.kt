// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.JetBrainsProductProperties
import org.jetbrains.intellij.build.TEST_FRAMEWORK_WITH_JAVA_RT
import org.jetbrains.intellij.build.createBuildTasks
import org.jetbrains.intellij.build.impl.copyDirWithFileFilter
import java.nio.file.Path
import java.util.function.Predicate

const val PYDEVD_PACKAGE: String = "pydevd_package"

abstract class PyCharmPropertiesBase : JetBrainsProductProperties() {
  override val baseFileName: String
    get() = "pycharm"

  init {
    reassignAltClickToMultipleCarets = true
    useSplash = true
    productLayout.addPlatformSpec(TEST_FRAMEWORK_WITH_JAVA_RT)
    buildCrossPlatformDistribution = true
    mavenArtifacts.additionalModules = mavenArtifacts.additionalModules.addAll(listOf(
      "intellij.java.compiler.antTasks",
      "intellij.platform.testFramework.common",
      "intellij.platform.testFramework.junit5",
      "intellij.platform.testFramework",
    ))
  }

  override suspend fun copyAdditionalFiles(context: BuildContext, targetDir: Path) {
    val tasks = createBuildTasks(context)
    tasks.zipSourcesOfModules(
      modules = listOf("intellij.python.community", "intellij.python.psi"),
      targetFile = Path.of("$targetDir/lib/src/pycharm-openapi-src.zip"),
      includeLibraries = false,
    )

    copyDirWithFileFilter(
      fromDir = getKeymapReferenceDirectory(context),
      targetDir = targetDir.resolve("help"),
      fileFilter = Predicate { it.toString().endsWith(".pdf") }
    )
  }

  open fun getKeymapReferenceDirectory(context: BuildContext): Path = context.paths.projectHome.resolve("python/help")
}
