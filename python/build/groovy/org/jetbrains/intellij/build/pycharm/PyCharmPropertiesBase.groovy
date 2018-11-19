/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.intellij.build.pycharm

import org.jetbrains.intellij.build.*

/**
 * @author nik
 */
abstract class PyCharmPropertiesBase extends ProductProperties {
  PyCharmPropertiesBase() {
    baseFileName = "pycharm"
    reassignAltClickToMultipleCarets = true
    productLayout.mainJarName = "pycharm.jar"
    productLayout.additionalPlatformJars.put("pycharm-pydev.jar", "intellij.python.pydev")
  }

  @Override
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
    def tasks = BuildTasks.create(context)
    tasks.zipSourcesOfModules(["intellij.python.pydev"], "$targetDirectory/lib/src/pycharm-pydev-src.zip")
    tasks.zipSourcesOfModules(["intellij.python.community", "intellij.python.psi"], "$targetDirectory/lib/src/pycharm-openapi-src.zip")

    context.ant.copy(todir: "$targetDirectory/helpers") {
      fileset(dir: "$context.paths.communityHome/python/helpers")
    }
    context.ant.copy(todir: "$targetDirectory/help", failonerror: false) {
      fileset(dir: "$context.paths.projectHome/python/help") {
        include(name: "*.pdf")
      }
    }

    new PyPrebuiltIndicesGenerator().generateResources(context)

    context.ant.copy(todir: "$targetDirectory/index", failonerror: !context.options.isInDevelopmentMode) {
      fileset(dir: "$context.paths.temp/index", erroronmissingdir: !context.options.isInDevelopmentMode) {
        include(name: "**")
      }
    }
  }

  @Override
  String getEnvironmentVariableBaseName(ApplicationInfoProperties applicationInfo) {
    "PYCHARM"
  }
}

class PyPrebuiltIndicesGenerator implements ResourcesGenerator {
  @Override
  File generateResources(BuildContext context) {
    CompilationTasks.create(context).compileModules(["intellij.python.tools"])
    List<String> buildClasspath = context.getModuleRuntimeClasspath(context.findModule("intellij.python.tools"), false)

    def zipPath = "$context.paths.temp/zips"

    context.ant.copy(todir: "$zipPath", failonerror: !context.options.isInDevelopmentMode) {
      fileset(dir: "$context.paths.projectHome/python-distributions", erroronmissingdir: !context.options.isInDevelopmentMode) {
        include(name: "*.zip")
      }
      fileset(dir: "$context.paths.projectHome/skeletons", erroronmissingdir: !context.options.isInDevelopmentMode) {
        include(name: "*.zip")
      }
    }

    def outputPath = "$context.paths.temp/index"

    context.ant.java(classname: "com.jetbrains.python.tools.PyPrebuiltIndicesGeneratorKt", fork: true) {
      jvmarg(line: "-ea -Xmx1000m")
      arg(value: zipPath)
      arg(value: outputPath)
      classpath {
        buildClasspath.each {
          pathelement(location: it)
        }
      }
    }

    return new File(outputPath)
  }
}