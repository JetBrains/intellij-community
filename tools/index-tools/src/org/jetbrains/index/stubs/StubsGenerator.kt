// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.index.stubs

import com.google.common.hash.HashCode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.stubs.*
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestApplicationManager
import com.intellij.util.indexing.*
import com.intellij.util.io.PersistentHashMap
import com.intellij.util.io.write
import junit.framework.TestCase
import org.jetbrains.index.IndexGenerator
import java.io.File
import java.nio.file.Paths
import java.util.*
import kotlin.system.exitProcess

open class StubsGenerator(private val stubsVersion: String, private val stubsStorageFilePath: String) :
  IndexGenerator<SerializedStubTree>(stubsStorageFilePath) {

  private val serializationManager = SerializationManagerImpl(File("$stubsStorageFilePath.names").toPath(), false)

  fun buildStubsForRoots(roots: Collection<VirtualFile>) {
    // ensure indexes initialized
    ReadAction.run<Throwable> {
      FileBasedIndex.getInstance().ensureUpToDate(ID.create<Any, Any>("Stubs"), null, null)
    }

    try {
      buildIndexForRoots(roots)
    }
    finally {
      Disposer.dispose(serializationManager)

      writeStubsVersionFile(stubsStorageFilePath, stubsVersion)
    }
  }

  override fun getIndexValue(fileContent: FileContentImpl): SerializedStubTree? {
    val stub = buildStubForFile(fileContent, serializationManager)
    if (stub == null) {
      return null
    }

    return SerializedStubTree.serializeStub(stub, serializationManager, StubForwardIndexExternalizer.createFileLocalExternalizer())
  }

  override fun createStorage(stubsStorageFilePath: String): PersistentHashMap<HashCode, SerializedStubTree> {
    return PersistentHashMap(File("$stubsStorageFilePath.input").toPath(),
                             HashCodeDescriptor.instance, GeneratingFullStubExternalizer())
  }

  open fun buildStubForFile(fileContent: FileContent, serializationManager: SerializationManagerImpl): Stub? {
    return ReadAction.compute<Stub, Throwable> { StubTreeBuilder.buildStubTree(fileContent) }
  }
}

private fun writeStubsVersionFile(stubsStorageFilePath: String, stubsVersion: String) {
  val stubSerializationVersion = FileBasedIndexExtension.EXTENSION_POINT_NAME.findExtensionOrFail(StubUpdatingIndex::class.java).version
  Paths.get("$stubsStorageFilePath.version").write("$stubSerializationVersion\n$stubsVersion")
}

fun mergeStubs(paths: List<String>, stubsFilePath: String, stubsFileName: String, projectPath: String, stubsVersion: String) {
  TestApplicationManager.getInstance()
  PlatformTestUtil.loadAndOpenProject(Paths.get(projectPath), Disposable {  })
  // we don't need a project here, but I didn't find a better way to wait until indices and components are initialized

  val stubExternalizer = GeneratingFullStubExternalizer()

  val storageFile = File(stubsFilePath, "$stubsFileName.input")
  if (storageFile.exists()) {
    storageFile.delete()
  }

  val storage = PersistentHashMap(storageFile.toPath(), HashCodeDescriptor.instance, stubExternalizer)

  val stringEnumeratorFile = File(stubsFilePath, "$stubsFileName.names")
  if (stringEnumeratorFile.exists()) {
    stringEnumeratorFile.delete()
  }

  val newSerializationManager = SerializationManagerImpl(stringEnumeratorFile.toPath(), false)

  val map = HashMap<HashCode, Int>()

  println("Writing results to ${storageFile.absolutePath}")

  for (path in paths) {
    println("Reading stubs from $path")
    var count = 0
    val fromStorageFile = File(path, "$stubsFileName.input")
    val fromStorage = PersistentHashMap(fromStorageFile, HashCodeDescriptor.instance, stubExternalizer)

    val serializationManager = SerializationManagerImpl(File(path, "$stubsFileName.names").toPath(), true)
    try {
      fromStorage.processKeysWithExistingMapping { key ->
        count++
        val value = fromStorage.get(key)

        // re-serialize stub tree to correctly enumerate strings in the new string enumerator
        val newForwardIndexSerializer = StubForwardIndexExternalizer.createFileLocalExternalizer()
        val newStubTree = value.reSerialize(newSerializationManager, newForwardIndexSerializer)

        if (storage.containsMapping(key)) {
          if (newStubTree != storage.get(key)) { // TODO: why are they slightly different???
            storage.get(key).stub

            val stub = value.stub
            val newStubTree2 = SerializedStubTree.serializeStub(stub,
                                                                newSerializationManager,
                                                                newForwardIndexSerializer)

            TestCase.assertTrue(newStubTree == newStubTree2) // wtf!!! why are they equal now???
          }
          map[key] = map[key]!! + 1
        }
        else {
          storage.put(key, newStubTree)
          map[key] = 1
        }
        true
      }

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

  writeStubsVersionFile(stringEnumeratorFile.nameWithoutExtension, stubsVersion)

  for (i in 2..paths.size) {
    val count = map.entries.stream().filter { e -> e.value == i }.count()
    println("Intersection between $i: ${"%.2f".format(if (total > 0) 100.0 * count / total else 0.0)}%")
  }

  exitProcess(0)
}

/**
 * Generates stubs for file content for different language levels returned by languageLevelIterator
 * and checks that they are all equal.
 */
abstract class LanguageLevelAwareStubsGenerator<T>(stubsVersion: String, stubsStorageFilePath: String) : StubsGenerator(stubsVersion,
                                                                                                                        stubsStorageFilePath) {
  companion object {
    val FAIL_ON_ERRORS: Boolean = System.getenv("STUB_GENERATOR_FAIL_ON_ERRORS")?.toBoolean() ?: false
  }

  abstract fun languageLevelIterator(): Iterator<T>

  abstract fun applyLanguageLevel(level: T)

  abstract fun defaultLanguageLevel(): T

  override fun buildStubForFile(fileContent: FileContent, serializationManager: SerializationManagerImpl): Stub? {
    var prevLanguageLevel: T? = null
    var prevStub: Stub? = null
    val iter = languageLevelIterator()
    try {
      for (languageLevel in iter) {

        applyLanguageLevel(languageLevel)

        // create new FileContentImpl, because it caches stub in user data
        val content = FileContentImpl.createByContent(fileContent.file, fileContent.content)

        val stub = super.buildStubForFile(content, serializationManager)

        if (stub != null) {
          val bytes = BufferExposingByteArrayOutputStream()
          serializationManager.serialize(stub, bytes)

          if (prevStub != null) {
            try {
              check(stub, prevStub)
            }
            catch (e: AssertionError) {
              val msg = "Stubs are different for ${content.file.path} between Python versions $prevLanguageLevel and $languageLevel.\n"
              // Debug info
              // TestCase.assertEquals(msg, DebugUtil.stubTreeToString(stub), DebugUtil.stubTreeToString(prevStub))
              TestCase.assertTrue(msg, DebugUtil.stubTreeToString(stub)==DebugUtil.stubTreeToString(prevStub))
              TestCase.fail(msg + "But DebugUtil.stubTreeToString values of stubs are unfortunately equal.")
            }
          }
        }

        prevLanguageLevel = languageLevel
        prevStub = stub
      }

      applyLanguageLevel(defaultLanguageLevel())

      return prevStub!!
    }
    catch (e: AssertionError) {
      if (FAIL_ON_ERRORS) {
        throw e
      }
      println("Can't generate universal stub for ${fileContent.file.path}")
      // Debug info
      // e.printStackTrace()
      return null
    }
  }
}

private fun check(stub: Stub, stub2: Stub) {
  TestCase.assertEquals(stub.stubType, stub2.stubType)
  val stubs = stub.childrenStubs
  val stubs2 = stub2.childrenStubs
  TestCase.assertEquals(stubs.size, stubs2.size)
  var i = 0
  val len = stubs.size
  while (i < len) {
    check(stubs[i], stubs2[i])
    ++i
  }
}

