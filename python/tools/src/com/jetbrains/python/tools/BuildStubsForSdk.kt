package com.jetbrains.python.tools

import com.google.common.hash.HashCode
import com.intellij.idea.IdeaTestApplication
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.stubs.*
import com.intellij.util.indexing.FileContentImpl
import com.intellij.util.io.PersistentHashMap
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.impl.stubs.PyPrebuiltStubsProvider.SDK_STUBS_STORAGE_NAME
import junit.framework.TestCase
import java.io.ByteArrayInputStream
import java.io.File
import java.util.*

/**
 * @author traff
 */

val stubsFileName = SDK_STUBS_STORAGE_NAME

fun main(args: Array<String>) {
  val app = IdeaTestApplication.getInstance()


  try {
    indexSdkAndStoreSerializedStubs("${PathManager.getHomePath()}/python/testData/empty",
                                    File(System.getenv("PYCHARM_PERF_ENVS"), "envs/py36_64").absolutePath,
                                    "${System.getProperty("user.dir")}/$stubsFileName")
  }
  catch (e: Exception) {
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

  val roots: List<VirtualFile> = sdk!!.rootProvider.getFiles(OrderRootType.CLASSES).asList()

  val hashing = FileContentHashing()


  val stubExternalizer = StubTreeExternalizer()
  val storage = PersistentHashMap<HashCode, SerializedStubTree>(File(stubsFilePath + ".input"),
                                                                HashCodeDescriptor.instance, stubExternalizer)

  val serializationManager = SerializationManagerImpl(File(stubsFilePath + ".names"))

  try {
    val map = HashMap<HashCode, Pair<String, SerializedStubTree>>()

    for (file in roots) {
      VfsUtilCore.visitChildrenRecursively(file, object : VirtualFileVisitor<Boolean>() {
        override fun visitFile(file: VirtualFile): Boolean {
          if (file.isDirectory && file.name == "parts") {
            return false
          }
          if (file.fileType == PythonFileType.INSTANCE) {
            val fileContent = FileContentImpl(file, file.contentsToByteArray())
            val stub = buildStubForFile(fileContent, serializationManager)
            val hashCode = hashing.hashString(fileContent)

            val stub2 = storage.get(hashCode)

            val bytes = BufferExposingByteArrayOutputStream()
            serializationManager.serialize(stub, bytes)


            val contentLength =
              if (file.fileType.isBinary) {
                -1
              }
              else {
                fileContent.psiFileForPsiDependentIndex.textLength
              }

            val stubTree = SerializedStubTree(bytes.internalBuffer, bytes.size(), stub, file.length, contentLength)
            val item = map.get(hashCode)
            if (item == null) {
              storage.put(hashCode, stubTree)
              map.put(hashCode, Pair(fileContent.contentAsText.toString(), stubTree))
            }
            else {
              TestCase.assertEquals(item.first, fileContent.contentAsText.toString())
              TestCase.assertTrue(stubTree == item.second)
            }
          }


          return true
        }
      })
    }
  }
  finally {
    storage.close()

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

private fun buildStubForFile(fileContent: FileContentImpl,
                             serializationManager: SerializationManagerImpl): Stub {
  var prevLanguageLevelBytes: ByteArray? = null
  var prevLanguageLevel: LanguageLevel? = null
  var prevStub: Stub? = null
  for (languageLevel in LanguageLevel.SUPPORTED_LEVELS) {
    LanguageLevel.FORCE_LANGUAGE_LEVEL = languageLevel

    val stub = ReadAction.compute<Stub, Throwable> { StubTreeBuilder.buildStubTree(fileContent) }
    val bytes = BufferExposingByteArrayOutputStream()
    serializationManager.serialize(stub!!, bytes)

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
