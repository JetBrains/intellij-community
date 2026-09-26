// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.junit5.framework.impl

import com.intellij.psi.PsiDirectory
import com.intellij.python.junit5Tests.framework.metaInfo.TestMetaInfoExtension.Companion.getTestClassInfo
import com.intellij.python.junit5Tests.framework.metaInfo.TestMetaInfoExtension.Companion.getTestMethodInfo
import com.intellij.python.junit5Tests.framework.psiFileFixture
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.junit5.fixture.LookupFixture
import com.intellij.testFramework.junit5.fixture.LookupFixtureExtension.Companion.getLookupFixtureManager
import com.intellij.testFramework.junit5.fixture.LookupFixtureExtension.Companion.registerImplicitFixtures
import com.jetbrains.python.junit5.framework.annotations.MultiFileTest
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.Extension
import org.junit.jupiter.api.extension.ExtensionContext
import kotlin.io.path.pathString

internal class PyTestDataExtension: Extension, BeforeEachCallback {

  override fun beforeEach(context: ExtensionContext) {
    val manager = context.getLookupFixtureManager()
    val codeInsightTestFixture = manager.getRequired<CodeInsightTestFixture>()
    val sourceRoot = manager.getRequired<PsiDirectory>()

    val testDataPath = context.getTestClassInfo().testDataPath
    val testCaseRelativePath = context.getTestMethodInfo().testCaseRelativePath
    if (testDataPath == null || testCaseRelativePath == null) return

    val codeInsightFixture = codeInsightTestFixture.get()
    codeInsightFixture.testDataPath = testDataPath.pathString

    val isMultiFile = context.testMethod.get().getAnnotation(MultiFileTest::class.java) != null

    if (isMultiFile) {
      val testSubDir = testCaseRelativePath.parent ?: error("Test case file $testCaseRelativePath does not have a parent directory")
      codeInsightFixture.copyDirectoryToProject(testSubDir.pathString, testSubDir.pathString)
    }
    else {
      codeInsightFixture.copyFileToProject(testCaseRelativePath.pathString, testCaseRelativePath.pathString)
    }

    // Injects the PSI file that is going to be tested
    sourceRoot.psiFileFixture(testCaseRelativePath).also {
      runBlocking {
        context.registerImplicitFixtures(listOf(LookupFixture(testCaseRelativePath.toString(),
                                                              it, true)), static = false)
      }
    }
  }
}