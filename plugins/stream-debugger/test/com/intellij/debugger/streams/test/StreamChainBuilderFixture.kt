// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.streams.test

import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.debugger.streams.core.wrapper.StreamChain
import com.intellij.debugger.streams.core.wrapper.StreamChainBuilder
import com.intellij.debugger.streams.psi.impl.JavaChainTransformerImpl
import com.intellij.debugger.streams.psi.impl.JavaStreamChainBuilder
import com.intellij.debugger.streams.psi.impl.PackageChainDetector
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightFixture
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightProjectFixture
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiElement
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.setUpJdk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Creates the fixture of the stream chain tests on the Java sources of the test data.
 *
 * Each test gets a new project with one Java module.
 * The module has the mock JDK of [jdkLevel] and the JetBrains annotations with `TYPE_USE`.
 * The project language level is [languageLevel].
 * The test data directory is [relativeTestPath] in the `testData` directory of the plugin.
 * Declare it as an instance field of a `@TestApplication` class.
 */
@JvmOverloads
fun streamChainBuilderFixture(
  relativeTestPath: String = "",
  jdkLevel: LanguageLevel = LanguageLevel.JDK_1_8,
  languageLevel: LanguageLevel = LanguageLevel.HIGHEST,
): TestFixture<StreamChainBuilderFixture> = testFixture("streamChainBuilderFixture") { context ->
  val codeInsight = codeInsightFixture(codeInsightProjectFixture()).init()
  val project = codeInsight.project
  val module = codeInsight.module
  val disposable = Disposer.newDisposable("streamChainBuilderFixture")
  withContext(Dispatchers.EDT) {
    setUpJdk(jdkLevel, project, module, disposable)
  }
  IdeaTestUtil.setProjectLanguageLevel(project, languageLevel, disposable)
  ModuleRootModificationUtil.updateModel(module, DefaultLightProjectDescriptor::addJetBrainsAnnotationsWithTypeUse)
  codeInsight.testDataPath = StreamDebuggerTestUtil.getTestDataPath(relativeTestPath)
  val fixture = StreamChainBuilderFixture(codeInsight, context.extensionContext.requiredTestMethod.name)
  initialized(fixture) {
    Disposer.dispose(disposable)
  }
}

/** The state and the helpers of a test that [streamChainBuilderFixture] creates. */
class StreamChainBuilderFixture internal constructor(
  /** The code insight fixture of the test. */
  val codeInsight: CodeInsightTestFixture,
  private val testMethodName: String,
) {
  /** The chain builder of the Java streams. */
  val chainBuilder: StreamChainBuilder = JavaStreamChainBuilder(
    JavaChainTransformerImpl(),
    PackageChainDetector.forJavaStreams("java.util.stream"),
  )

  /** The name of the test method without the `test` prefix. */
  fun getTestName(lowercaseFirstLetter: Boolean): String = PlatformTestUtil.getTestName(testMethodName, lowercaseFirstLetter)

  /** Configures the file `<test name>.java` of the test data directory and gives the element at the caret. */
  fun configureAndGetElementAtCaret(): PsiElement {
    codeInsight.configureByFile("/" + getTestName(false) + ".java")
    val elementAtCaret = ReadAction.computeBlocking<PsiElement?, RuntimeException> {
      val offset = codeInsight.editor.caretModel.currentCaret.offset
      DebuggerUtilsEx.findElementAt(codeInsight.file, offset)
    }
    assertNotNull(elementAtCaret)
    return elementAtCaret!!
  }

  /** Configures the file of the test, checks that a chain exists at the caret, and builds the chains. */
  fun buildChains(): List<StreamChain> {
    val elementAtCaret = configureAndGetElementAtCaret()
    return ReadAction.computeBlocking<List<StreamChain>, RuntimeException> {
      assertTrue(chainBuilder.isChainExists(elementAtCaret))
      chainBuilder.build(elementAtCaret)
    }
  }
}
