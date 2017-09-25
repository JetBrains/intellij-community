package com.jetbrains.python.tools

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.stubs.PrebuiltStubsProviderBase.Companion.PREBUILT_INDICES_PATH_PROPERTY
import com.intellij.psi.stubs.PrebuiltStubsProviderBase.Companion.SDK_STUBS_STORAGE_NAME
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.PythonModuleTypeBase
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyFileElementType
import org.jetbrains.index.stubs.LanguageLevelAwareStubsGenerator
import org.jetbrains.index.stubs.ProjectSdkStubsGenerator
import org.jetbrains.index.stubs.mergeStubs
import org.junit.Assert
import java.io.File

/**
 * @author traff
 */

val stubsFileName = SDK_STUBS_STORAGE_NAME

val MERGE_STUBS_FROM_PATHS = "MERGE_STUBS_FROM_PATHS"

fun getBaseDirValue(): String? {
  val path: String? = System.getProperty(PREBUILT_INDICES_PATH_PROPERTY)

  if (path == null) {
    Assert.fail("$PREBUILT_INDICES_PATH_PROPERTY variable is not defined")
  }
  else
    if (File(path).exists()) {
      return path
    }
    else {
      Assert.fail("Directory $path doesn't exist")
    }
  return null
}

val stubsVersion = PyFileElementType.INSTANCE.stubVersion.toString()

fun main(args: Array<String>) {
  val baseDir = getBaseDirValue()

  if (System.getenv().containsKey(MERGE_STUBS_FROM_PATHS)) {

    mergeStubs(System.getenv(MERGE_STUBS_FROM_PATHS).split(File.pathSeparatorChar), "$baseDir", stubsFileName,
               "${PathManager.getHomePath()}/python/testData/empty", stubsVersion)
  }
  else {
    PyProjectSdkStubsGenerator().buildStubs(baseDir!!)
  }
}

class PyProjectSdkStubsGenerator : ProjectSdkStubsGenerator() {
  override val moduleTypeId: String
    get() = PythonModuleTypeBase.PYTHON_MODULE

  override fun createSdkProducer(sdkPath: String) = createPythonSdkProducer(sdkPath)

  override fun createStubsGenerator() = PyStubsGenerator(stubsVersion)

  override val root: String?
    get() = System.getenv(PYCHARM_PYTHONS)
}

class PyStubsGenerator(stubsVersion: String) : LanguageLevelAwareStubsGenerator<LanguageLevel>(stubsVersion) {
  override fun defaultLanguageLevel(): LanguageLevel = LanguageLevel.getDefault()

  override fun languageLevelIterator() = LanguageLevel.SUPPORTED_LEVELS.iterator()

  override fun applyLanguageLevel(level: LanguageLevel) {
    LanguageLevel.FORCE_LANGUAGE_LEVEL = level
  }

  override val fileFilter: VirtualFileFilter
    get() = VirtualFileFilter { file: VirtualFile ->
      if (file.isDirectory && file.name == "parts") throw NoSuchElementException()
      else file.fileType == PythonFileType.INSTANCE
    }
}










