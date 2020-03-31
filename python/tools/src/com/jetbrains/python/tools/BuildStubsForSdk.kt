// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.tools

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.stubs.PrebuiltStubsProviderBase.Companion.PREBUILT_INDICES_PATH_PROPERTY
import com.intellij.psi.stubs.PrebuiltStubsProviderBase.Companion.SDK_STUBS_STORAGE_NAME
import com.jetbrains.python.PyNames
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.PythonHelper
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyFileElementType
import com.jetbrains.python.sdk.PythonSdkUtil
import org.jetbrains.index.stubs.LanguageLevelAwareStubsGenerator
import org.jetbrains.index.stubs.ProjectSdkStubsGenerator
import org.jetbrains.index.stubs.mergeStubs
import org.junit.Assert
import java.io.File
import kotlin.system.exitProcess

const val stubsFileName = SDK_STUBS_STORAGE_NAME
const val MERGE_STUBS_FROM_PATHS = "MERGE_STUBS_FROM_PATHS"
const val PACK_STDLIB_FROM_PATH = "PACK_STDLIB_FROM_PATH"

fun getBaseDirValue(): String? {
  val path: String? = System.getProperty(PREBUILT_INDICES_PATH_PROPERTY)

  if (path == null) {
    Assert.fail("$PREBUILT_INDICES_PATH_PROPERTY variable is not defined")
  }
  else {
    if (!File(path).exists()) {
      File(path).mkdirs()
    }
    return path
  }

  return null
}

val stubsVersion: String = PyFileElementType.INSTANCE.stubVersion.toString()

fun main() {
  val baseDir = getBaseDirValue()!!
  when {
    System.getenv().containsKey(MERGE_STUBS_FROM_PATHS) -> {
      mergeStubs(System.getenv(MERGE_STUBS_FROM_PATHS).split(File.pathSeparatorChar), baseDir, stubsFileName,
                 "${PathManager.getHomePath()}/python/testData/empty", stubsVersion)
    }
    System.getenv().containsKey(PACK_STDLIB_FROM_PATH) -> {
      packStdlibFromPath(baseDir, System.getenv(PACK_STDLIB_FROM_PATH))
    }
    else -> {
      PyProjectSdkStubsGenerator().buildStubs(baseDir)
    }
  }
}

private fun packStdlibFromPath(baseDir: String, root: String) {
  try {
    for (python in File(root).listFiles()!!) {
      if (python.name.startsWith(".")) {
        continue
      }
      val sdkHome = python.absolutePath

      val executable = File(PythonSdkUtil.getPythonExecutable(sdkHome) ?: throw AssertionError("No python on $sdkHome"))
      println("Packing stdlib of $sdkHome")

      val cph = CapturingProcessHandler(GeneralCommandLine(executable.absolutePath, PythonHelper.GENERATOR3.asParamString(), "-u", baseDir))
      val output = cph.runProcess()
      println(output.stdout + output.stderr)
    }
  }
  finally {
    exitProcess(0)
  }
}

private class PyProjectSdkStubsGenerator : ProjectSdkStubsGenerator() {
  override val moduleTypeId: String
    get() = PyNames.PYTHON_MODULE_ID

  override fun createSdkProducer(sdkPath: String): (Project, Module) -> Sdk = createPythonSdkProducer(sdkPath)

  override fun createStubsGenerator(stubsFilePath: String): PyStubsGenerator = PyStubsGenerator(stubsFilePath)

  override val root: String?
    get() = System.getenv(PYCHARM_PYTHONS)
}

internal class PyStubsGenerator(stubsStorageFilePath: String) : LanguageLevelAwareStubsGenerator<LanguageLevel>(PyFileElementType.INSTANCE.stubVersion.toString(), stubsStorageFilePath) {
  override fun defaultLanguageLevel(): LanguageLevel = LanguageLevel.getDefault()

  override fun languageLevelIterator(): MutableIterator<LanguageLevel> = LanguageLevel.SUPPORTED_LEVELS.iterator()

  override fun applyLanguageLevel(level: LanguageLevel) {
    LanguageLevel.FORCE_LANGUAGE_LEVEL = level
  }

  override val fileFilter: VirtualFileFilter
    get() = VirtualFileFilter { file: VirtualFile ->
      if (file.isDirectory && file.name == "parts") throw NoSuchElementException()
      else file.fileType == PythonFileType.INSTANCE
    }
}










