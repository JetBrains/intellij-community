// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.pycharm


import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.intellij.build.JetBrainsProductProperties

@CompileStatic
abstract class PyCharmPropertiesBase extends JetBrainsProductProperties {

  PyCharmPropertiesBase() {
    baseFileName = "pycharm"
    reassignAltClickToMultipleCarets = true
    useSplash = true
    productLayout.mainJarName = "pycharm.jar"
    productLayout.withAdditionalPlatformJar("testFramework.jar",
                                            "intellij.platform.testFramework.core",
                                            "intellij.platform.testFramework",
                                            "intellij.tools.testsBootstrap",
                                            "intellij.java.rt")

    buildCrossPlatformDistribution = true
    mavenArtifacts.additionalModules = [
      "intellij.java.compiler.antTasks",
      "intellij.platform.testFramework"
    ]
  }

  @Override
  @CompileStatic(TypeCheckingMode.SKIP)
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
    def tasks = BuildTasks.create(context)
    tasks.zipSourcesOfModules(["intellij.python.community", "intellij.python.psi"], "$targetDirectory/lib/src/pycharm-openapi-src.zip")

    context.ant.copy(todir: "$targetDirectory/help", failonerror: false) {
      fileset(dir: getKeymapReferenceDirectory(context)) {
        include(name: "*.pdf")
      }
    }
  }

  @Override
  String getEnvironmentVariableBaseName(ApplicationInfoProperties applicationInfo) {
    return "PYCHARM"
  }
  
  String getKeymapReferenceDirectory(BuildContext context) {
    return "$context.paths.projectHome/python/help"
  }
}
