// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs

import com.intellij.idea.TestFor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.stubs.SerializationManagerEx
import com.intellij.psi.stubs.Stub
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.io.StringRef
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.PyClass
import com.jetbrains.python.psi.stubs.PyClassStub
import com.jetbrains.python.psi.stubs.PyDataclassStub
import com.jetbrains.python.psi.stubs.PyNamedTupleStub
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

@TestFor(classes = [PyCustomizableStubElementType::class], issues = ["PY-93913", "PY-57893"])
@Subsystems.CodeInsight
@Layers.Functional
class PyCustomStubFormatTest : PyCodeInsightTestCase() {
  @TestDisposable
  lateinit var testDisposable: Disposable

  private val classes = """
    import dataclasses


    @dataclasses.dataclass
    class A:
        x: int


    class B:
        pass
  """.trimIndent()

  private fun serialize(code: String): ByteArray {
    val file = myFixture.configureByText("a.py", code)
    return runReadActionBlocking {
      val bytes = ByteArrayOutputStream()
      SerializationManagerEx.getInstanceEx().serialize((file as PsiFileImpl).calcStubTree().root, bytes)
      bytes.toByteArray()
    }
  }

  private fun deserialize(bytes: ByteArray): List<Stub> =
    SerializationManagerEx.getInstanceEx().deserialize(ByteArrayInputStream(bytes)).childrenStubs

  @Test
  fun `a custom stub reads back through its type`() {
    val stubs = deserialize(serialize(classes)).filterIsInstance<PyClassStub>()

    assertEquals(listOf("A" to true, "B" to false), stubs.map { it.name to (it.getCustomStub(PyDataclassStub::class.java) != null) })
  }

  @Test
  fun `a class with a custom stub of a type that is gone reads with no custom stub`() {
    val bytes = serialize(classes)
    ExtensionTestUtil.maskExtensions(PyCustomClassStubType.EP_NAME, emptyList(), testDisposable)

    val stubs = deserialize(bytes).filterIsInstance<PyClassStub>()

    assertEquals(listOf("A" to false, "B" to false), stubs.map { it.name to (it.getCustomStub(PyDataclassStub::class.java) != null) })
  }

  @Test
  fun `a target with a custom stub of a type that is gone reads with no custom stub`() {
    val code = """
      from typing import NamedTuple

      Point = NamedTuple("Point", [("x", int)])
    """.trimIndent()
    val target = { bytes: ByteArray -> deserialize(bytes).filterIsInstance<PyTargetExpressionStub>().single() }
    val bytes = serialize(code)
    assertTrue(target(bytes).getCustomStub(PyNamedTupleStub::class.java) != null, "The named tuple stub before the mask")
    ExtensionTestUtil.maskExtensions(CustomTargetExpressionStubType.EP_NAME, emptyList(), testDisposable)

    val stub = target(bytes)

    assertEquals("Point" to null, stub.name to stub.getCustomStub(PyNamedTupleStub::class.java))
  }

  @Test
  fun `a name of a custom stub resolves through the names of its file`() {
    ExtensionTestUtil.maskExtensions(PyCustomClassStubType.EP_NAME, listOf(NameStubType()), testDisposable)

    val stubs = deserialize(serialize(classes)).filterIsInstance<PyClassStub>()

    assertEquals(listOf("A", "B"), stubs.map { it.getCustomStub(NameStub::class.java)?.name?.string })
  }

  @Test
  fun `a custom stub type that leaves bytes unread fails with its name`() {
    ExtensionTestUtil.maskExtensions(PyCustomClassStubType.EP_NAME, listOf(ShortReadStubType()), testDisposable)
    val bytes = serialize(classes)

    // The stubs of a file are read lazily, on the first access.
    val error = assertThrows<Throwable> { deserialize(bytes).forEach { it.stubType } }

    val messages = generateSequence(error) { it.cause }.filterIsInstance<IOException>().mapNotNull { it.message }.toList()
    assertTrue(messages.any { "ShortReadStubType left 4 of its" in it }, messages.toString())
  }
}

/** A custom stub with the class name, which it writes with [StubOutputStream.writeName], and a marker. */
private class NameStub(val name: StringRef?, private val type: Class<out PyCustomClassStubType<*>>) : PyCustomClassStub {
  override fun getTypeClass(): Class<out PyCustomClassStubType<*>> = type

  override fun serialize(stream: StubOutputStream) {
    stream.writeName(name?.string)
    stream.writeInt(7)
  }
}

private class NameStubType : PyCustomClassStubType<NameStub>() {
  override fun createStub(psi: PyClass): NameStub = NameStub(StringRef.fromString(psi.name), NameStubType::class.java)

  override fun deserializeStub(stream: StubInputStream): NameStub {
    val name = stream.readName()
    stream.readInt()
    return NameStub(name, NameStubType::class.java)
  }
}

/** Reads the name of a [NameStub] but not its marker. */
private class ShortReadStubType : PyCustomClassStubType<NameStub>() {
  override fun createStub(psi: PyClass): NameStub = NameStub(StringRef.fromString(psi.name), ShortReadStubType::class.java)

  override fun deserializeStub(stream: StubInputStream): NameStub = NameStub(stream.readName(), ShortReadStubType::class.java)
}
