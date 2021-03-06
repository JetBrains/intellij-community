// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.pycharm

import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import org.jetbrains.intellij.build.ApplicationInfoProperties
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildTasks
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.JetBrainsProductProperties

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

@CompileStatic
abstract class PyCharmPropertiesBase extends JetBrainsProductProperties {
  protected String dependenciesPath

  PyCharmPropertiesBase() {
    baseFileName = "pycharm"
    reassignAltClickToMultipleCarets = true
    productLayout.mainJarName = "pycharm.jar"
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
  @CompileStatic(TypeCheckingMode.SKIP)
  void copyAdditionalFiles(BuildContext context, String targetDirectory) {
    def tasks = BuildTasks.create(context)
    tasks.zipSourcesOfModules(["intellij.python.community", "intellij.python.psi"], "$targetDirectory/lib/src/pycharm-openapi-src.zip")

    context.ant.copy(todir: "$targetDirectory/help", failonerror: false) {
      fileset(dir: "$context.paths.projectHome/python/help") {
        include(name: "*.pdf")
      }
    }

    // Don't generate indices and stubs when building pycharm only from sources
    context.executeStep("Generate indices and stubs", PyCharmBuildOptions.GENERATE_INDICES_AND_STUBS_STEP) {
      Path indicesFolder = PyCharmBuildOptions.getFolderForIndicesAndStubs(context)
      if (!Files.exists(indicesFolder)) {
        Files.createDirectories(indicesFolder)
        generateStubsAndIndices(context, indicesFolder)
      }

      context.messages.block("Copy indices and stubs") {
        context.ant.copy(todir: "$targetDirectory/index", failonerror: !context.options.isInDevelopmentMode) {
          fileset(dir: indicesFolder.toString(), erroronmissingdir: !context.options.isInDevelopmentMode) {
            include(name: "**")
          }
        }
      }
    }
  }

  @Override
  String getEnvironmentVariableBaseName(ApplicationInfoProperties applicationInfo) {
    return "PYCHARM"
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  protected void unzipArchives(BuildContext context, Path destination) {
    Path tempFolder = context.paths.tempDir.resolve("zips")
    Files.createDirectories(tempFolder)
    tempFolder.toFile().deleteOnExit()
    context.ant.copy(todir: tempFolder.toString(), failonerror: !context.options.isInDevelopmentMode) {
      fileset(dir: "$context.paths.projectHome/python-distributions", erroronmissingdir: !context.options.isInDevelopmentMode) {
        include(name: "*.zip")
      }
      fileset(dir: "$context.paths.projectHome/skeletons", erroronmissingdir: !context.options.isInDevelopmentMode) {
        include(name: "*.zip")
      }
    }

    unzipAllZips(tempFolder, destination, context)
  }

  private static Path unzipAllZips(Path tempFolder, Path destination, BuildContext buildContext) {
    Files.walkFileTree(tempFolder, new SimpleFileVisitor<Path>() {
      @Override
      FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        if (file.fileName.toString().endsWith(".zip")) {
          doUnzip(buildContext, file, destination)
        }
        return FileVisitResult.CONTINUE
      }
    })
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  private static void doUnzip(BuildContext context, Path file, Path destination) {
    context.ant.unzip(src: file.toString(), dest: "${destination.toString()}/${file.fileName}")
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  protected List<Integer> getStubVersion(BuildContext context) {
    CompilationTasks.create(context).compileModules(["intellij.python.tools"])
    List<String> buildClasspath = context.getModuleRuntimeClasspath(context.findModule("intellij.python.tools"), false)

    File outputFile = File.createTempFile("GetPyStubsVersionKt_output", "txt")
    context.ant.java(classname: "com.jetbrains.python.tools.GetPyStubsVersionKt",
                     fork: true,
                     failonerror: !context.options.isInDevelopmentMode,
                     output: outputFile) {
      classpath {
        buildClasspath.each {
          pathelement(location: it)
        }
      }
    }

    List<String> stubsVersion = outputFile.readLines().takeRight(2)
    outputFile.deleteOnExit()
    return [stubsVersion[0].trim().toInteger(), stubsVersion[1].trim().toInteger()]
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  protected void generateUniversalStubs(BuildContext context, Path from, Path to) {
    CompilationTasks.create(context).compileModules(["intellij.python.tools"])
    List<String> buildClasspath = context.getModuleRuntimeClasspath(context.findModule("intellij.python.tools"), false)

    context.messages.block("Generate universal stubs") {
      context.ant.java(classname: "com.jetbrains.python.tools.PythonUniversalStubsBuilderKt",
                       fork: true,
                       failonerror: !context.options.isInDevelopmentMode) {
        jvmarg(line: "-ea -Xmx1000m")
        arg(value: from.toString())
        arg(value: to.toString())
        classpath {
          buildClasspath.each {
            pathelement(location: it)
          }
        }
      }
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  protected void generateIndices(BuildContext context, Path from, Path to) {
    CompilationTasks.create(context).compileModules(["intellij.python.tools"])
    List<String> buildClasspath = context.getModuleRuntimeClasspath(context.findModule("intellij.python.tools"), false)

    context.ant.java(classname: "com.jetbrains.python.tools.IndicesBuilderKt",
                     fork: true,
                     failonerror: !context.options.isInDevelopmentMode) {
      jvmarg(line: "-ea -Xmx1000m")
      arg(value: from.toString())
      arg(value: to.toString())
      classpath {
        buildClasspath.each {
          pathelement(location: it)
        }
      }
    }
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  protected void generateStubsAndIndices(BuildContext context, Path temporaryIndexFolder) {
    Path folderWithUnzipContent = PyCharmBuildOptions.getTemporaryFolderForUnzip(context)
    Files.createDirectories(folderWithUnzipContent)
    unzipArchives(context, folderWithUnzipContent)

    boolean forceGenerate = false
    if (PyCharmBuildOptions.usePrebuiltStubs) {
      File stubsArchive = new File(context.paths.projectHome, PyCharmBuildOptions.prebuiltStubsArchive)
      context.messages.block("Unzip prebuilt stubs ${stubsArchive.absolutePath}") {
        context.ant.unzip(src: stubsArchive.absolutePath, dest: temporaryIndexFolder.toString())

        try {
          List<String> stubsVersions = Files.readAllLines(temporaryIndexFolder.resolve("Python/sdk-stubs.version"))
          Integer firstVersionFromStubs = stubsVersions[0].toInteger()
          Integer secondVersionFromStubs = stubsVersions[1].toInteger()

          context.messages.info("Stubs serialization version from prebuilt stubs - $firstVersionFromStubs")
          context.messages.info("Stub updating index version from prebuilt stubs - $secondVersionFromStubs")

          List<Integer> versionsFromSources = getStubVersion(context)
          Integer firstVersionFromSources = versionsFromSources[0]
          Integer secondVersionFromSources = versionsFromSources[1]

          context.messages.info("Stubs serialization version from sources - $firstVersionFromSources")
          context.messages.info("Stub updating index version from prebuilt stubs - $secondVersionFromSources")

          forceGenerate = firstVersionFromStubs != firstVersionFromSources || secondVersionFromStubs != secondVersionFromSources
        }
        catch (Exception e) {
          context.messages.warning("Force generating stubs inplace")
          context.messages.warning(e.message)
          forceGenerate = true
        }
      }
    }

    if (forceGenerate || !PyCharmBuildOptions.usePrebuiltStubs) {
      FileUtil.delete(temporaryIndexFolder)
      Files.createDirectories(temporaryIndexFolder)
      generateUniversalStubs(context, folderWithUnzipContent, temporaryIndexFolder)
    }

    context.messages.block("Generate indices") {
      generateIndices(context, folderWithUnzipContent, temporaryIndexFolder)
    }

    FileUtil.delete(folderWithUnzipContent)
  }
}
