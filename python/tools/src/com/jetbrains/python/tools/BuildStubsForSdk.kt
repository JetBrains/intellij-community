package com.jetbrains.python.tools

import com.intellij.idea.IdeaTestApplication
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.stubs.SerializationManagerImpl
import com.intellij.psi.stubs.Stub
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyFileElementType
import com.jetbrains.python.psi.impl.stubs.PyPrebuiltStubsProvider.PREBUILT_INDEXES_PATH_PROPERTY
import com.jetbrains.python.psi.impl.stubs.PyPrebuiltStubsProvider.SDK_STUBS_STORAGE_NAME
import junit.framework.TestCase
import org.jetbrains.index.stubs.StubsGenerator
import org.jetbrains.index.stubs.mergeStubs
import org.junit.Assert
import java.io.ByteArrayInputStream
import java.io.File
import java.util.*
import kotlin.NoSuchElementException

/**
 * @author traff
 */

val stubsFileName = SDK_STUBS_STORAGE_NAME

val MERGE_STUBS_FROM_PATHS = "MERGE_STUBS_FROM_PATHS"


fun getBaseDirValue(): String? {
  val path: String? = System.getProperty(PREBUILT_INDEXES_PATH_PROPERTY)

  if (path == null) {
    Assert.fail("$PREBUILT_INDEXES_PATH_PROPERTY variable is not defined")
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



fun main(args: Array<String>) {
  val baseDir = getBaseDirValue()

  if (System.getenv().containsKey(MERGE_STUBS_FROM_PATHS)) {
    mergeStubs(System.getenv(MERGE_STUBS_FROM_PATHS).split(File.pathSeparatorChar), "$baseDir/$stubsFileName",
               "${PathManager.getHomePath()}/python/testData/empty", PyFileElementType.INSTANCE.stubVersion.toString())
  }
  else {
    buildStubs(baseDir!!)
  }
}


fun buildStubs(baseDir: String) {
  val app = IdeaTestApplication.getInstance()


  try {
    val root = System.getenv(PYCHARM_PYTHONS)

    for (python in File(root).listFiles()) {
      indexSdkAndStoreSerializedStubs("${PathManager.getHomePath()}/python/testData/empty",
                                      python.absolutePath,
                                      "$baseDir/$stubsFileName")
    }
  }
  catch (e: Throwable) {
    e.printStackTrace()
  }
  finally {
    UIUtil.invokeAndWaitIfNeeded(Runnable {
      WriteAction.run<Throwable> {
        app.dispose()
      }
    })
    System.exit(0) //TODO: graceful shutdown
  }
}

fun indexSdkAndStoreSerializedStubs(projectPath: String, sdkPath: String, stubsFilePath: String) {
  val pair = openProjectWithSdk(projectPath, sdkPath)

  val project = pair.first
  val sdk = pair.second

  try {

    val roots: List<VirtualFile> = sdk!!.rootProvider.getFiles(OrderRootType.CLASSES).asList()

    val stubsGenerator = PyStubsGenerator(PyFileElementType.INSTANCE.stubVersion.toString())

    stubsGenerator.buildStubsForRoots(stubsFilePath, roots,
                                      VirtualFileFilter { file: VirtualFile ->
                                        if (file.isDirectory && file.name == "parts") throw NoSuchElementException()
                                        else file.fileType == PythonFileType.INSTANCE
                                      })

  }
  finally {
    LanguageLevel.FORCE_LANGUAGE_LEVEL = LanguageLevel.getDefault()
    UIUtil.invokeAndWaitIfNeeded(Runnable {
      ProjectManager.getInstance().closeProject(project!!)
      WriteAction.run<Throwable> {
        Disposer.dispose(project)
        SdkConfigurationUtil.removeSdk(sdk)
      }
    })
  }
}

class PyStubsGenerator(stubsVersion: String) : StubsGenerator(stubsVersion) {
  override fun buildStubForFile(fileContent: FileContentImpl, serializationManager: SerializationManagerImpl): Stub {
    var prevLanguageLevelBytes: ByteArray? = null
    var prevLanguageLevel: LanguageLevel? = null
    var prevStub: Stub? = null
    for (languageLevel in LanguageLevel.SUPPORTED_LEVELS) {
      LanguageLevel.FORCE_LANGUAGE_LEVEL = languageLevel

      val stub = super.buildStubForFile(fileContent, serializationManager)

      val bytes = BufferExposingByteArrayOutputStream()
      serializationManager.serialize(stub, bytes)

      if (prevLanguageLevelBytes != null) {
        if (!Arrays.equals(bytes.toByteArray(), prevLanguageLevelBytes)) {
          val stub2 = serializationManager.deserialize(ByteArrayInputStream(prevLanguageLevelBytes))
          val msg = "Stubs are different for ${fileContent.file.path} between Python versions $prevLanguageLevel and $languageLevel.\n"
          TestCase.assertEquals(msg, DebugUtil.stubTreeToString(stub), DebugUtil.stubTreeToString(stub2))
          TestCase.fail(msg + "But DebugUtil.stubTreeToString values of stubs are unfortunately equal.")
        }
      }
      prevLanguageLevelBytes = bytes.toByteArray()
      prevLanguageLevel = languageLevel
      prevStub = stub
    }

    return prevStub!!
  }
}







