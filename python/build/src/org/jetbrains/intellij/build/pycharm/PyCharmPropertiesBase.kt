// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.pycharm

import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.*

import java.nio.file.Path

@CompileStatic
abstract class PyCharmPropertiesBase extends JetBrainsProductProperties {
  @Override
  String getBaseFileName() {
    return "pycharm"
  }

  PyCharmPropertiesBase() {
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
    mavenArtifacts.additionalModules = mavenArtifacts.additionalModules.addAll(List.of(
      "intellij.java.compiler.antTasks",
      "intellij.platform.testFramework.common",
      "intellij.platform.testFramework.junit5",
      "intellij.platform.testFramework",
      ))
  }

  @Override
  void copyAdditionalFilesBlocking(BuildContext context, String targetDirectory) {
    def tasks = BuildTasks.create(context)
    tasks.zipSourcesOfModulesBlocking(List.of("intellij.python.community", "intellij.python.psi"), Path.of("$targetDirectory/lib/src/pycharm-openapi-src.zip"))

    new FileSet(Path.of(getKeymapReferenceDirectory(context)))
      .include("*.pdf")
      .copyToDir(Path.of(targetDirectory, "help"))
  }

  @Override
  String getEnvironmentVariableBaseName(ApplicationInfoProperties applicationInfo) {
    return "PYCHARM"
  }
  
  String getKeymapReferenceDirectory(BuildContext context) {
    return "${context.paths.projectHome}/python/help"
  }
}
