// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.*
import java.nio.file.Path

internal const val PYDEVD_PACKAGE = "pydevd_package"

abstract class PyCharmPropertiesBase : JetBrainsProductProperties() {
  override val baseFileName: String
    get() = "pycharm"

  init {
    reassignAltClickToMultipleCarets = true
    useSplash = true
    productLayout.mainJarName = "pycharm.jar"
    productLayout.withAdditionalPlatformJar(
      "testFramework.jar",
      "intellij.platform.testFramework.core",
      "intellij.platform.testFramework.impl",
      "intellij.platform.testFramework.common",
      "intellij.platform.testFramework.junit5",
      "intellij.platform.testFramework",
      "intellij.tools.testsBootstrap",
      "intellij.java.rt",
      )

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

  override fun getEnvironmentVariableBaseName(appInfo: ApplicationInfoProperties) = "PYCHARM"

  open fun getKeymapReferenceDirectory(context: BuildContext) = "${context.paths.projectHome}/python/help"
}