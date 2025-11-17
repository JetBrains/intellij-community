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
    val classLevelManager = context.getLookupFixtureManager()
    val codeInsightTestFixture = classLevelManager.getRequired<CodeInsightTestFixture>()
    val sourceRoot = classLevelManager.getRequired<PsiDirectory>()

    val testDataPath = context.getTestClassInfo().testDataPath
    val testCaseFilePath = context.getTestMethodInfo().testCaseFilePath
    if (testDataPath == null || testCaseFilePath == null) return

    val codeInsightFixture = codeInsightTestFixture.get()
    codeInsightFixture.testDataPath = testDataPath.pathString

    val isMultiFile = context.testMethod.get().getAnnotation(MultiFileTest::class.java) != null

    if (isMultiFile) {
      val testSubDir = testCaseFilePath.parent ?: error("Test case file $testCaseFilePath does not have a parent directory")
      codeInsightFixture.copyDirectoryToProject(testSubDir.pathString, testSubDir.pathString)
    }
    else {
      codeInsightFixture.copyFileToProject(testCaseFilePath.pathString, testCaseFilePath.pathString)
    }

    // Injects the PSI file that is going to be tested
    sourceRoot.psiFileFixture(testCaseFilePath).also {
      runBlocking {
        context.registerImplicitFixtures(listOf(LookupFixture(testCaseFilePath.toString(),
                                                              it, true)), static = false)
      }
    }
  }
}