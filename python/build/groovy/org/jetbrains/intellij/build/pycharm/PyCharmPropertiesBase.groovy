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

import static org.jetbrains.intellij.build.pycharm.PyCharmBuildOptions.GENERATE_INDICES_AND_STUBS_STEP

/**
 * @author nik
 */
abstract class PyCharmPropertiesBase extends ProductProperties {

  PyCharmPropertiesBase() {
    baseFileName = "pycharm"
    reassignAltClickToMultipleCarets = true
    productLayout.mainJarName = "pycharm.jar"
    productLayout.additionalPlatformJars.put("pycharm-pydev.jar", "intellij.python.pydev")
    productLayout.additionalPlatformJars.putAll("testFramework.jar",
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
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
    def tasks = BuildTasks.create(context)
    tasks.zipSourcesOfModules(["intellij.python.pydev"], "$targetDirectory/lib/src/pycharm-pydev-src.zip")
    tasks.zipSourcesOfModules(["intellij.python.community", "intellij.python.psi"], "$targetDirectory/lib/src/pycharm-openapi-src.zip")

    context.ant.copy(todir: "$targetDirectory/helpers") {
      fileset(dir: "$context.paths.communityHome/python/helpers") {
        exclude(name: "**/setup.py")
        exclude(name: "pydev/test**/**")
        exclude(name: "tests/")
      }
    }
    context.ant.copy(todir: "$targetDirectory/help", failonerror: false) {
      fileset(dir: "$context.paths.projectHome/python/help") {
        include(name: "*.pdf")
      }
    }

    // Don't generate indices and stubs when building pycharm only from sources
    context.executeStep("Generate indices and stubs", GENERATE_INDICES_AND_STUBS_STEP) {
      File indicesFolder = PyCharmBuildOptions.getFolderForIndicesAndStubs(context)
      if (!indicesFolder.exists()) {
        indicesFolder.mkdirs()
        generateStubsAndIndices(context, indicesFolder)
      }

      context.messages.block("Copy indices and stubs") {
        context.ant.copy(todir: "$targetDirectory/index", failonerror: !context.options.isInDevelopmentMode) {
          fileset(dir: indicesFolder.absolutePath, erroronmissingdir: !context.options.isInDevelopmentMode) {
            include(name: "**")
          }
        }
      }
    }
  }

  @Override
  String getEnvironmentVariableBaseName(ApplicationInfoProperties applicationInfo) {
    "PYCHARM"
  }

  protected void unzipArchives(BuildContext context, File destination) {
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

  protected int getStubVersion(BuildContext context) {
    CompilationTasks.create(context).compileModules(["intellij.python.tools"])
    List<String> buildClasspath = context.getModuleRuntimeClasspath(context.findModule("intellij.python.tools"), false)

    context.ant.java(classname: "com.jetbrains.python.tools.GetPyStubsVersionKt",
                     fork: true,
                     failonerror: !context.options.isInDevelopmentMode,
                     outputproperty: "stubsVersion") {
      classpath {
        buildClasspath.each {
          pathelement(location: it)
        }
      }
    }
    return context.ant.project.properties.stubsVersion as int
  }

  protected void generateUniversalStubs(BuildContext context, File from, File to) {
    CompilationTasks.create(context).compileModules(["intellij.python.tools"])
    List<String> buildClasspath = context.getModuleRuntimeClasspath(context.findModule("intellij.python.tools"), false)

    context.messages.block("Generate universal stubs") {
      context.ant.java(classname: "com.jetbrains.python.tools.PythonUniversalStubsBuilderKt",
                       fork: true,
                       failonerror: !context.options.isInDevelopmentMode) {
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

  protected void generateIndices(BuildContext context, File from, File to) {
    CompilationTasks.create(context).compileModules(["intellij.python.tools"])
    List<String> buildClasspath = context.getModuleRuntimeClasspath(context.findModule("intellij.python.tools"), false)

    context.ant.java(classname: "com.jetbrains.python.tools.IndicesBuilderKt",
                     fork: true,
                     failonerror: !context.options.isInDevelopmentMode) {
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

  protected void generateStubsAndIndices(BuildContext context, File temporaryIndexFolder) {
    File folderWithUnzipContent = PyCharmBuildOptions.getTemporaryFolderForUnzip(context)
    folderWithUnzipContent.mkdirs()
    unzipArchives(context, folderWithUnzipContent)

    boolean forceGenerate = false
    if (PyCharmBuildOptions.usePrebuiltStubs) {
      File stubsArchive = new File(context.paths.projectHome, PyCharmBuildOptions.prebuiltStubsArchive)
      context.messages.block("Unzip prebuilt stubs ${stubsArchive.absolutePath}") {
        context.ant.unzip(src: stubsArchive.absolutePath, dest: "${temporaryIndexFolder.absolutePath}")

        try {
          String stubsVersionPath = new FileNameFinder()
            .getFileNames("${temporaryIndexFolder.absolutePath}/Python", "sdk-stubs.version").
            first()
          int stubsFromArchiveVersion = new File(stubsVersionPath).readLines()[0].toInteger()
          context.messages.info("Stubs version $stubsFromArchiveVersion")
          int stubVersion = getStubVersion(context)
          context.messages.info("But should be $stubVersion")

          forceGenerate = stubsFromArchiveVersion != stubVersion
        }
        catch (ignored) {
          forceGenerate = true
        }
      }
    }

    if (forceGenerate || !PyCharmBuildOptions.usePrebuiltStubs) {
      temporaryIndexFolder.deleteDir()
      temporaryIndexFolder.mkdir()
      generateUniversalStubs(context, folderWithUnzipContent, temporaryIndexFolder)
    }

    context.messages.block("Generate indices") {
      generateIndices(context, folderWithUnzipContent, temporaryIndexFolder)
    }

    folderWithUnzipContent.deleteDir()
  }
}