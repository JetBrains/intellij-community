// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.*
import java.nio.file.Path

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

  override fun copyAdditionalFilesBlocking(context: BuildContext, targetDirectory: String) {
    val tasks = BuildTasks.create(context)
    tasks.zipSourcesOfModulesBlocking(listOf("intellij.python.community", "intellij.python.psi"), Path.of("$targetDirectory/lib/src/pycharm-openapi-src.zip"))

    FileSet(Path.of(getKeymapReferenceDirectory(context)))
      .include("*.pdf")
      .copyToDir(Path.of(targetDirectory, "help"))
  }

  open fun getKeymapReferenceDirectory(context: BuildContext) = "${context.paths.projectHome}/python/help"
}
