// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.k2.debugger.test

import com.intellij.debugger.impl.hotswap.HotSwapClassShape
import com.intellij.debugger.impl.hotswap.HotSwapIncompatibilityReasons
import com.intellij.debugger.impl.hotswap.HotSwapSourceChangeCompatibilityCheckerTestUtil
import com.intellij.debugger.impl.hotswap.JvmBaseSourceFileChangeCompatibilityChecker
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiFile
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.xdebugger.impl.hotswap.HotSwapChangesCompatibility
import com.intellij.xdebugger.impl.hotswap.SourceFileChange
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.debugger.core.hotswap.KotlinHotSwapSourceChangeCompatibilityChecker
import org.jetbrains.kotlin.idea.test.KotlinLightCodeInsightFixtureTestCase
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class KotlinHotSwapSourceChangeCompatibilityCheckerTest : KotlinLightCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR
  override fun runInDispatchThread(): Boolean = false

  @Test
  fun `body code changed`() {
    assertCompatible(
      "body code changed",
      """
        class A { fun f(): Int = 1 }
      """.trimIndent(),
      """
        class A { fun f(): Int = 2 }
      """.trimIndent(),
    )
  }

  @Test
  fun `package body code changed`() {
    assertCompatible(
      "package body code changed",
      """
        package p

        class A { fun f(): Int = 1 }
      """.trimIndent(),
      """
        package p

        class A { fun f(): Int = 2 }
      """.trimIndent(),
    )
  }

  @Test
  fun `top level class added`() {
    assertCompatible(
      "top level class added",
      """
        class A { fun f(): Int = 1 }
      """.trimIndent(),
      """
        class A { fun f(): Int = 1 }
        class B { fun g(): Int = 2 }
      """.trimIndent(),
    )
  }

  @Test
  fun `top level class removed`() {
    assertCompatible(
      "top level class removed",
      """
        class A { fun f(): Int = 1 }
        class B { fun g(): Int = 2 }
      """.trimIndent(),
      """
        class A { fun f(): Int = 1 }
      """.trimIndent(),
    )
  }

  @Test
  fun `top level function body changed`() {
    assertCompatible(
      "top level function body changed",
      """
        fun f(): Int = 1
      """.trimIndent(),
      """
        fun f(): Int = 2
      """.trimIndent(),
    )
  }

  @Test
  fun `method added`() {
    assertIncompatible(
      "method added",
      """
        class A { fun f(): Int = 1 }
      """.trimIndent(),
      """
        class A { fun f(): Int = 1; fun g(): Int = 2 }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.methodAdded(),
    )
  }

  @Test
  fun `method removed`() {
    assertIncompatible(
      "method removed",
      """
        class A { fun f(): Int = 1; fun g(): Int = 2 }
      """.trimIndent(),
      """
        class A { fun f(): Int = 1 }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.methodRemoved(),
    )
  }

  @Test
  fun `method parameter type changed`() {
    assertIncompatible(
      "method parameter type changed",
      """
        class A { fun f(value: Int): Int = value }
      """.trimIndent(),
      """
        class A { fun f(value: Long): Int = 1 }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.signatureModified(),
    )
  }

  @Test
  fun `method parameter type qualified name changed`() {
    assertCompatible(
      "method parameter type qualified name changed",
      """
        class A { fun f(value: List<String>): Int = value.size }
      """.trimIndent(),
      """
        class A { fun f(value: kotlin.collections.List<kotlin.String>): Int = value.size }
      """.trimIndent(),
    )
  }

  @Test
  fun `method return type changed`() {
    assertIncompatible(
      "method return type changed",
      """
        class A { fun f(): Int = 1 }
      """.trimIndent(),
      """
        class A { fun f(): Long = 1 }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.signatureModified(),
    )
  }

  @Test
  fun `method return type qualified name changed`() {
    assertCompatible(
      "method return type qualified name changed",
      """
        class A { fun f(): List<String> = emptyList() }
      """.trimIndent(),
      """
        class A { fun f(): kotlin.collections.List<kotlin.String> = emptyList() }
      """.trimIndent(),
    )
  }

  @Test
  fun `method modifier changed`() {
    assertIncompatible(
      "method modifier changed",
      """
        open class A { fun f(): Int = 1 }
      """.trimIndent(),
      """
        open class A { open fun f(): Int = 1 }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.methodModifiersChanged(),
    )
  }

  @Test
  fun `property added`() {
    assertIncompatible(
      "property added",
      """
        class A { fun f(): Int = 1 }
      """.trimIndent(),
      """
        class A { val value: Int = 1; fun f(): Int = 1 }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.signatureModified(),
    )
  }

  @Test
  fun `property removed`() {
    assertIncompatible(
      "property removed",
      """
        class A { val value: Int = 1; fun f(): Int = value }
      """.trimIndent(),
      """
        class A { fun f(): Int = 1 }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.signatureModified(),
    )
  }

  @Test
  fun `property type changed`() {
    assertIncompatible(
      "property type changed",
      """
        class A { val value: Int = 1 }
      """.trimIndent(),
      """
        class A { val value: Long = 1 }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.signatureModified(),
    )
  }

  @Test
  fun `property type qualified name changed`() {
    assertCompatible(
      "property type qualified name changed",
      """
        class A { val value: List<String> = emptyList() }
      """.trimIndent(),
      """
        class A { val value: kotlin.collections.List<kotlin.String> = emptyList() }
      """.trimIndent(),
    )
  }

  @Test
  fun `property modifier changed`() {
    assertIncompatible(
      "property modifier changed",
      """
        class A { var value: String = "" }
      """.trimIndent(),
      """
        class A { lateinit var value: String }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.signatureModified(),
    )
  }

  @Test
  fun `property mutability changed`() {
    assertIncompatible(
      "property mutability changed",
      """
        class A { val value: Int = 1 }
      """.trimIndent(),
      """
        class A { var value: Int = 1 }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.signatureModified(),
    )
  }

  @Test
  fun `top level property mutability changed`() {
    assertIncompatible(
      "top level property mutability changed",
      """
        val value: Int = 1
      """.trimIndent(),
      """
        var value: Int = 1
      """.trimIndent(),
      HotSwapIncompatibilityReasons.signatureModified(),
    )
  }

  @Test
  fun `top level function added`() {
    assertIncompatible(
      "top level function added",
      """
        fun f(): Int = 1
      """.trimIndent(),
      """
        fun f(): Int = 1
        fun g(): Int = 2
      """.trimIndent(),
      HotSwapIncompatibilityReasons.methodAdded(),
    )
  }

  @Test
  fun `top level property type changed`() {
    assertIncompatible(
      "top level property type changed",
      """
        val value: Int = 1
      """.trimIndent(),
      """
        val value: Long = 1
      """.trimIndent(),
      HotSwapIncompatibilityReasons.signatureModified(),
    )
  }

  @Test
  fun `primary constructor property added`() {
    assertIncompatible(
      "primary constructor property added",
      """
        class A
      """.trimIndent(),
      """
        class A(val value: Int)
      """.trimIndent(),
      HotSwapIncompatibilityReasons.signatureModified(),
    )
  }

  @Test
  fun `primary constructor property type changed`() {
    assertIncompatible(
      "primary constructor property type changed",
      """
        class A(val value: Int)
      """.trimIndent(),
      """
        class A(val value: Long)
      """.trimIndent(),
      HotSwapIncompatibilityReasons.signatureModified(),
    )
  }

  @Test
  fun `primary constructor property modifier changed`() {
    assertIncompatible(
      "primary constructor property modifier changed",
      """
        class A(val value: Int)
      """.trimIndent(),
      """
        class A(private val value: Int)
      """.trimIndent(),
      HotSwapIncompatibilityReasons.signatureModified(),
    )
  }

  @Test
  fun `primary constructor property mutability changed`() {
    assertIncompatible(
      "primary constructor property mutability changed",
      """
        class A(val value: Int)
      """.trimIndent(),
      """
        class A(var value: Int)
      """.trimIndent(),
      HotSwapIncompatibilityReasons.signatureModified(),
    )
  }

  @Test
  fun `constructor parameter type changed`() {
    assertIncompatible(
      "constructor parameter type changed",
      """
        class A(value: Int)
      """.trimIndent(),
      """
        class A(value: Long)
      """.trimIndent(),
      HotSwapIncompatibilityReasons.signatureModified(),
    )
  }

  @Test
  fun `constructor modifier changed`() {
    assertIncompatible(
      "constructor modifier changed",
      """
        class A { constructor(value: Int) {} }
      """.trimIndent(),
      """
        class A { private constructor(value: Int) {} }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.methodModifiersChanged(),
    )
  }

  @Test
  fun `class modifier changed`() {
    assertIncompatible(
      "class modifier changed",
      """
        class A { fun f(): Int = 1 }
      """.trimIndent(),
      """
        open class A { fun f(): Int = 1 }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.classModifiersChanged(),
    )
  }

  @Test
  fun `class supertype changed`() {
    assertIncompatible(
      "class supertype changed",
      """
        interface B
        class A : B { fun f(): Int = 1 }
      """.trimIndent(),
      """
        interface C
        class A : C { fun f(): Int = 1 }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.structureModified(),
    )
  }

  @Test
  fun `class supertype qualified name changed`() {
    assertCompatible(
      "class supertype qualified name changed",
      """
        package p

        interface B
        class A : B { fun f(): Int = 1 }
      """.trimIndent(),
      """
        package p

        interface B
        class A : p.B { fun f(): Int = 2 }
      """.trimIndent(),
    )
  }

  @Test
  fun `class kind changed`() {
    assertIncompatible(
      "class kind changed",
      """
        class A { fun f(): Int = 1 }
      """.trimIndent(),
      """
        interface A { fun f(): Int }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.structureModified(),
    )
  }

  @Test
  fun `object kind changed`() {
    assertIncompatible(
      "object kind changed",
      """
        object A { fun f(): Int = 1 }
      """.trimIndent(),
      """
        class A { fun f(): Int = 1 }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.structureModified(),
    )
  }

  @Test
  fun `enum class kind changed`() {
    assertIncompatible(
      "enum class kind changed",
      """
        enum class A { ENTRY }
      """.trimIndent(),
      """
        class A
      """.trimIndent(),
      HotSwapIncompatibilityReasons.structureModified(),
    )
  }

  @Test
  fun `annotation class kind changed`() {
    assertIncompatible(
      "annotation class kind changed",
      """
        annotation class A
      """.trimIndent(),
      """
        class A
      """.trimIndent(),
      HotSwapIncompatibilityReasons.structureModified(),
    )
  }

  @Test
  fun `enum entry method added`() {
    assertIncompatible(
      "enum entry method added",
      """
        enum class A { ENTRY { fun f(): Int = 1 } }
      """.trimIndent(),
      """
        enum class A { ENTRY { fun f(): Int = 1; fun g(): Int = 2 } }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.methodAdded(),
    )
  }

  @Test
  fun `nested class method added`() {
    assertIncompatible(
      "nested class method added",
      """
        class A { class Inner { fun f(): Int = 1 } }
      """.trimIndent(),
      """
        class A { class Inner { fun f(): Int = 1; fun g(): Int = 2 } }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.methodAdded(),
    )
  }

  @Test
  fun `nested class added`() {
    assertIncompatible(
      "nested class added",
      """
        class A { fun f(): Int = 1 }
      """.trimIndent(),
      """
        class A { class Inner; fun f(): Int = 1 }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.structureModified(),
    )
  }

  @Test
  fun `nested class removed`() {
    assertIncompatible(
      "nested class removed",
      """
        class A { class Inner; fun f(): Int = 1 }
      """.trimIndent(),
      """
        class A { fun f(): Int = 1 }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.structureModified(),
    )
  }

  @Test
  fun `anonymous class body code changed`() {
    assertCompatible(
      "anonymous class body code changed",
      """
        interface IntFactory { fun get(): Int }
        class A { fun f(): IntFactory = object : IntFactory { override fun get(): Int = 1 } }
      """.trimIndent(),
      """
        interface IntFactory { fun get(): Int }
        class A { fun f(): IntFactory = object : IntFactory { override fun get(): Int = 2 } }
      """.trimIndent(),
    )
  }

  @Test
  fun `anonymous class method added`() {
    assertIncompatible(
      "anonymous class method added",
      """
        interface IntFactory { fun get(): Int }
        class A { fun f(): IntFactory = object : IntFactory { override fun get(): Int = 1 } }
      """.trimIndent(),
      """
        interface IntFactory { fun get(): Int }
        class A { fun f(): IntFactory = object : IntFactory { override fun get(): Int = 1; fun extra(): Int = 2 } }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.methodAdded(),
    )
  }

  @Test
  fun `anonymous class captured variable changed`() {
    assertIncompatible(
      "anonymous class captured variable changed",
      """
        interface IntFactory { fun get(): Int }
        class A { fun f(first: Int, second: Int): IntFactory = object : IntFactory { override fun get(): Int = first } }
      """.trimIndent(),
      """
        interface IntFactory { fun get(): Int }
        class A { fun f(first: Int, second: Int): IntFactory = object : IntFactory { override fun get(): Int = second } }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.signatureModified(),
    )
  }

  @Test
  fun `lambda body code changed`() {
    assertCompatible(
      "lambda body code changed",
      """
        class A { fun f(): () -> Int = { 1 } }
      """.trimIndent(),
      """
        class A { fun f(): () -> Int = { 2 } }
      """.trimIndent(),
    )
  }

  @Test
  fun `lambda added`() {
    assertIncompatible(
      "lambda added",
      """
        class A { fun f(): (() -> Int)? = null }
      """.trimIndent(),
      """
        class A { fun f(): (() -> Int)? = { 1 } }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.methodAdded(),
    )
  }

  @Test
  fun `lambda captured variable changed`() {
    assertCompatible(
      "lambda captured variable changed",
      """
        class A { fun f(first: Int, second: Int): () -> Int = { first } }
      """.trimIndent(),
      """
        class A { fun f(first: Int, second: Int): () -> Int = { second } }
      """.trimIndent(),
    )
  }

  @Test
  fun `enum entry added`() {
    assertIncompatible(
      "enum entry added",
      """
        enum class A { FIRST }
      """.trimIndent(),
      """
        enum class A { FIRST, SECOND }
      """.trimIndent(),
      HotSwapIncompatibilityReasons.structureModified(),
    )
  }

  @Test
  fun `current file syntax error`() {
    assertIncompatible(
      "current file syntax error",
      """
        class A { fun f(): Int = 1 }
      """.trimIndent(),
      """
        class A { fun f(): Int = 2
      """.trimIndent(),
      HotSwapIncompatibilityReasons.compilationProblems(),
    )
  }

  @Test
  fun `old file syntax error`() {
    assertUnknown(
      """
        class A { fun f(): Int = 1
      """.trimIndent(),
      """
        class A { fun f(): Int = 2 }
      """.trimIndent(),
    )
  }

  @Test
  fun `dumb mode returns unknown`() {
    val checker = object : JvmBaseSourceFileChangeCompatibilityChecker(project, KotlinFileType.INSTANCE) {
      override fun resolutionFingerprint(file: PsiFile): ResolutionFingerprint = ResolutionFingerprint("", "", emptySet())

      context(_: Context)
      override fun buildClassShapes(file: PsiFile): Map<String, HotSwapClassShape> {
        throw IndexNotReadyException.create()
      }
    }
    DumbModeTestUtils.runInDumbModeSynchronously(project) {
      val currentFile = myFixture.addFileToProject("A.kt", "class A")
      val compatibility = runBlocking { checker.getCompatibility(SourceFileChange(currentFile.virtualFile, "class A")) }
      assertSame(HotSwapChangesCompatibility.Unknown, compatibility)
    }
  }

  @Test
  fun `old inferred function return type`() {
    assertCompatible(
      "old inferred function return type",
      """
        class A { fun f() = 1 }
      """.trimIndent(),
      """
        class A { fun f(): Int = 2 }
      """.trimIndent(),
    )
  }

  @Test
  fun `current inferred function return type`() {
    assertCompatible(
      "current inferred function return type",
      """
        class A { fun f(): Int = 1 }
      """.trimIndent(),
      """
        class A { fun f() = 2 }
      """.trimIndent(),
    )
  }

  @Test
  fun `current inferred property type`() {
    assertCompatible(
      "current inferred property type",
      """
        class A { val value: Int = 1 }
      """.trimIndent(),
      """
        class A { val value = 2 }
      """.trimIndent(),
    )
  }

  @Test
  fun `current inferred top level function return type`() {
    assertCompatible(
      "current inferred top level function return type",
      """
        fun f(): Int = 1
      """.trimIndent(),
      """
        fun f() = 2
      """.trimIndent(),
    )
  }

  @Test
  fun `current inferred top level property type`() {
    assertCompatible(
      "current inferred top level property type",
      """
        val value: Int = 1
      """.trimIndent(),
      """
        val value = 2
      """.trimIndent(),
    )
  }

  private fun assertCompatible(name: String, before: String, after: String) {
    assertSame(name, HotSwapChangesCompatibility.Compatible, classify(before, after))
  }

  private fun assertIncompatible(name: String, before: String, after: String, reason: String) {
    val compatibility = classify(before, after)
    assertTrue(name, compatibility is HotSwapChangesCompatibility.Incompatible)
    assertEquals(name, reason, (compatibility as HotSwapChangesCompatibility.Incompatible).reason)
  }

  private fun assertUnknown(before: String, after: String) {
    assertSame(HotSwapChangesCompatibility.Unknown, classify(before, after, validateOriginal = false))
  }

  private fun classify(before: String, after: String, validateOriginal: Boolean = true): HotSwapChangesCompatibility =
    HotSwapSourceChangeCompatibilityCheckerTestUtil.classifyDocumentChange(
      project,
      KotlinHotSwapSourceChangeCompatibilityChecker(project),
      myFixture.addFileToProject("A.kt", before),
      before,
      after,
      validateOriginal,
    )
}
