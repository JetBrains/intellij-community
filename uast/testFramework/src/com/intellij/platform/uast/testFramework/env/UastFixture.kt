// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.uast.testFramework.env

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import org.jetbrains.annotations.TestOnly
import org.jetbrains.uast.UFile
import org.jetbrains.uast.UastFacade

/**
 * The fixture of the UAST tests. Create it with [uastFixture].
 *
 * [doTest] and [configureUFile] configure a file of the test data and convert it to a [UFile].
 * [doTest] calls the check in a read action.
 * A test that reads the [UFile] of [configureUFile] needs its own read action.
 */
@TestOnly
class UastFixture internal constructor(
  /**
   * The code insight fixture of the test.
   */
  val fixture: LightJavaCodeInsightTestFixture,
  private val check: ((String, UFile) -> Unit)?,
) {
  /**
   * Configures [testName] of the test data, and calls the check of [uastFixture] with [testName] and the [UFile].
   */
  fun doTest(testName: String) {
    doTest(testName, requireNotNull(check) { "uastFixture has no check" })
  }

  /**
   * Configures [testName] of the test data, and calls [checkCallback] with [testName] and the [UFile] in a read action.
   */
  fun doTest(testName: String, checkCallback: (String, UFile) -> Unit) {
    val uFile = configureUFile(testName)
    runReadActionBlocking {
      checkCallback(testName, uFile)
    }
  }

  /**
   * Configures [testName] of the test data, and returns its [UFile].
   */
  fun configureUFile(testName: String): UFile {
    val psiFile = fixture.configureByFile(testName)
    return ReadAction.computeBlocking<UFile, RuntimeException> {
      val uFile = UastFacade.convertElementWithParent(psiFile, null) ?: error("Can't get UFile for $testName")
      uFile as? UFile ?: throw AssertionError(uFile.toString())
    }
  }
}

/**
 * Creates a [UastFixture] for one test. Declare it as an instance field.
 *
 * The test data path is [testDataPath].
 * [check] is the check of [UastFixture.doTest] without a callback.
 */
@TestOnly
fun uastFixture(
  projectFixture: TestFixture<LightJavaProject>,
  testDataPath: String,
  check: ((String, UFile) -> Unit)? = null,
): TestFixture<UastFixture> = testFixture("uastFixture") {
  val fixture = lightJavaCodeInsightFixture(projectFixture).init()
  fixture.testDataPath = testDataPath
  initialized(UastFixture(fixture, check)) {}
}
