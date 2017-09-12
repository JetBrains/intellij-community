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
import com.intellij.openapi.util.io.FileUtil
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
import com.jetbrains.python.psi.PyFileElementType
import com.jetbrains.python.psi.impl.stubs.PyPrebuiltStubsProvider.PREBUILT_INDEXES_PATH_PROPERTY
import com.jetbrains.python.psi.impl.stubs.PyPrebuiltStubsProvider.SDK_STUBS_STORAGE_NAME
import junit.framework.TestCase
import org.junit.Assert
import java.io.ByteArrayInputStream
import java.io.File
import java.util.*

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

val baseDir = getBaseDirValue()

fun main(args: Array<String>) {
  if (System.getenv().containsKey(MERGE_STUBS_FROM_PATHS)) {
    mergeStubs(System.getenv(MERGE_STUBS_FROM_PATHS).split(File.pathSeparatorChar))
  }
  else {
    buildStubs()
  }
}

fun mergeStubs(paths: List<String>) {
  val app = IdeaTestApplication.getInstance()
  val project = ProjectManager.getInstance().loadAndOpenProject("${PathManager.getHomePath()}/python/testData/empty")!!
  // we don't need a project here, but I didn't find a better way to wait until indexes and components are initialized

  try {
    val stubExternalizer = StubTreeExternalizer()

    val stubsFilePath = "$baseDir/$stubsFileName"

    val storageFile = File(stubsFilePath + ".input")
    if (storageFile.exists()) {
      storageFile.delete()
    }

    val storage = PersistentHashMap<HashCode, SerializedStubTree>(storageFile,
                                                                  HashCodeDescriptor.instance, stubExternalizer)

    val stringEnumeratorFile = File(stubsFilePath + ".names")
    if (stringEnumeratorFile.exists()) {
      stringEnumeratorFile.delete()
    }

    val newSerializationManager = SerializationManagerImpl(stringEnumeratorFile)

    val map = HashMap<HashCode, Int>()

    println("Writing results to $stubsFilePath")

    for (path in paths) {
      println("Reading stubs from $path")
      var count = 0
      val fromStorageFile = File(path, stubsFileName + ".input")
      val fromStorage = PersistentHashMap<HashCode, SerializedStubTree>(fromStorageFile,
                                                                        HashCodeDescriptor.instance, stubExternalizer)

      val serializationManager = SerializationManagerImpl(File(path, stubsFileName + ".names"))

      try {
        fromStorage.processKeysWithExistingMapping(
          { key ->
            count++
            val value = fromStorage.get(key)

            val stub = value.getStub(false, serializationManager)

            // re-serialize stub tree to correctly enumerate strings in the new string enumerator
            val bytes = BufferExposingByteArrayOutputStream()
            newSerializationManager.serialize(stub, bytes)

            val newStubTree = SerializedStubTree(bytes.internalBuffer, bytes.size(), null, value.byteContentLength,
                                                 value.charContentLength)

            if (storage.containsMapping(key)) {
              if (newStubTree != storage.get(key)) { // TODO: why are they slightly different???
                val s = storage.get(key).getStub(false, newSerializationManager)

                val bytes2 = BufferExposingByteArrayOutputStream()
                newSerializationManager.serialize(stub, bytes2)

                val newStubTree2 = SerializedStubTree(bytes2.internalBuffer, bytes2.size(), null, value.byteContentLength,
                                                      value.charContentLength)

                TestCase.assertTrue(newStubTree == newStubTree2) // wtf!!! why are they equal now???
              }
              map.put(key, map.get(key)!! + 1)
            }
            else {
              storage.put(key, newStubTree)
              map.put(key, 1)
            }
            true
          })

      }
      finally {
        fromStorage.close()
        Disposer.dispose(serializationManager)
      }

      println("Items in ${fromStorageFile.absolutePath}: $count")
    }

    storage.close()
    Disposer.dispose(newSerializationManager)

    val total = map.size

    println("Total items in storage: $total")

    writeVersionFile()

    for (i in 2..paths.size) {
      val count = map.entries.stream().filter({ e -> e.value == i }).count()
      println("Intersection between $i: ${"%.2f".format(if (total > 0) 100.0 * count / total else 0.0)}%")
    }
  }
  finally {
    UIUtil.invokeAndWaitIfNeeded(Runnable {
      ProjectManager.getInstance().closeProject(project)
      WriteAction.run<Throwable> {
        Disposer.dispose(project)
        Disposer.dispose(app)
      }
    })

  }

  System.exit(0)
}

fun buildStubs() {
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

  val roots: List<VirtualFile> = sdk!!.rootProvider.getFiles(OrderRootType.CLASSES).asList()

  val hashing = FileContentHashing()


  val stubExternalizer = StubTreeExternalizer()
  val storage = PersistentHashMap<HashCode, SerializedStubTree>(File(stubsFilePath + ".input"),
                                                                HashCodeDescriptor.instance, stubExternalizer)

  println("Writing stubs to ${storage.baseFile.absolutePath}")

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
    Disposer.dispose(serializationManager)

    writeVersionFile()

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

private fun writeVersionFile() {
  FileUtil.writeToFile(File(baseDir, stubsFileName + ".version"), PyFileElementType.INSTANCE.stubVersion.toString())
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
