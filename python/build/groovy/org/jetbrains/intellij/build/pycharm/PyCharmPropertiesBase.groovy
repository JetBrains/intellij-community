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

import groovy.io.FileType
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
      fileset(dir: "$context.paths.communityHome/python/helpers") {
        exclude(name: "**/setup.py")
        exclude(name: "pydev/test**/**")
      }
    }
    context.ant.copy(todir: "$targetDirectory/help", failonerror: false) {
      fileset(dir: "$context.paths.projectHome/python/help") {
        include(name: "*.pdf")
      }
    }

    File folderWithUnzipContent = new File(context.paths.temp, "unzips")
    folderWithUnzipContent.mkdirs()
    unzipArchives(context, folderWithUnzipContent)

    File temporaryIndexFolder = new File(context.paths.temp, "index")
    temporaryIndexFolder.deleteDir()
    temporaryIndexFolder.mkdirs()

    boolean forceGenerate = false
    if (PyCharmBuildOptions.usePrebuiltStubs) {
      File stubsArchive = new File(context.paths.projectHome, PyCharmBuildOptions.prebuiltStubsArchive)
      context.ant.echo("Unzip prebuilt stubs ${stubsArchive.absolutePath}")
      context.ant.unzip(src: stubsArchive.absolutePath, dest: "${temporaryIndexFolder.absolutePath}")

      try {
        String stubsVersionPath = new FileNameFinder()
          .getFileNames("${temporaryIndexFolder.absolutePath}/Python", "sdk-stubs.version").
          first()
        int stubsFromArchiveVersion = new File(stubsVersionPath).readLines()[0].toInteger()
        context.ant.echo("Stubs version $stubsFromArchiveVersion")
        int stubVersion = getStubVersion(context)
        context.ant.echo("But should be $stubVersion")

        forceGenerate = stubsFromArchiveVersion != stubVersion
      }
      catch (ignored) {
        forceGenerate = true
      }
    }

    if (forceGenerate || !PyCharmBuildOptions.usePrebuiltStubs) {
      temporaryIndexFolder.deleteDir()
      temporaryIndexFolder.mkdir()
      generateUniversalStubs(context, folderWithUnzipContent, temporaryIndexFolder)
    }

    context.ant.echo("Generate indices")
    generateIndices(context, folderWithUnzipContent, temporaryIndexFolder)
    context.ant.echo("Copy indices")
    context.ant.copy(todir: "$targetDirectory/index", failonerror: !context.options.isInDevelopmentMode) {
      fileset(dir: temporaryIndexFolder.absolutePath, erroronmissingdir: !context.options.isInDevelopmentMode) {
        include(name: "**")
      }
    }

    folderWithUnzipContent.deleteDir()
    temporaryIndexFolder.deleteDir()
  }

  @Override
  String getEnvironmentVariableBaseName(ApplicationInfoProperties applicationInfo) {
    "PYCHARM"
  }

  private void unzipArchives(BuildContext context, File destination) {
    File tempFolder = new File(context.paths.temp, "zips")
    tempFolder.mkdirs()
    tempFolder.deleteOnExit()
    context.ant.copy(todir: tempFolder.absolutePath, failonerror: !context.options.isInDevelopmentMode) {
      fileset(dir: "$context.paths.projectHome/python-distributions", erroronmissingdir: !context.options.isInDevelopmentMode) {
        include(name: "*.zip")
      }
      fileset(dir: "$context.paths.projectHome/skeletons", erroronmissingdir: !context.options.isInDevelopmentMode) {
        include(name: "*.zip")
      }
    }

    tempFolder.eachFileRecurse(FileType.FILES) {
      if (it.name.endsWith('.zip')) {
        context.ant.unzip(src: it.absolutePath, dest: "${destination.absolutePath}/${it.name}")
      }
    }
  }

  private int getStubVersion(BuildContext context) {
    List<String> buildClasspath = context.getModuleRuntimeClasspath(context.findModule("intellij.python.tools"), false)

    context.ant.java(classname: "com.jetbrains.python.tools.GetPyStubsVersionKt", fork: true, outputproperty: "stubsVersion") {
      classpath {
        buildClasspath.each {
          pathelement(location: it)
        }
      }
    }
    return context.ant.project.properties.stubsVersion as int
  }

  private void generateUniversalStubs(BuildContext context, File from, File to) {
    CompilationTasks.create(context).compileModules(["intellij.python.tools"])
    List<String> buildClasspath = context.getModuleRuntimeClasspath(context.findModule("intellij.python.tools"), false)

    context.messages.block("Generate universal stubs") {
      context.ant.java(classname: "com.jetbrains.python.tools.PythonUniversalStubsBuilderKt", fork: true) {
        jvmarg(line: "-ea -Xmx1000m")
        arg(value: from.absolutePath)
        arg(value: to.absolutePath)
        classpath {
          buildClasspath.each {
            pathelement(location: it)
          }
        }
      }
    }
  }

  private void generateIndices(BuildContext context, File from, File to) {
    CompilationTasks.create(context).compileModules(["intellij.python.tools"])
    List<String> buildClasspath = context.getModuleRuntimeClasspath(context.findModule("intellij.python.tools"), false)

    context.ant.java(classname: "com.jetbrains.python.tools.IndicesBuilderKt", fork: true) {
      jvmarg(line: "-ea -Xmx1000m")
      arg(value: from.absolutePath)
      arg(value: to.absolutePath)
      classpath {
        buildClasspath.each {
          pathelement(location: it)
        }
      }
    }
  }
}