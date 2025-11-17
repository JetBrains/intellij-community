// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections

import com.intellij.psi.PsiFile
import com.intellij.python.junit5Tests.framework.FolderTest
import com.intellij.python.junit5Tests.framework.metaInfo.Repository
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.jetbrains.python.junit5.framework.annotations.InspectionTest
import com.jetbrains.python.junit5.framework.annotations.PyCodeInsightTestApplication
import com.jetbrains.python.junit5.framework.util.doTestByFile

@PyCodeInsightTestApplication
@InspectionTest(PyMissingConstructorInspection::class)
@TestClassInfo(Repository.PY_COMMUNITY)
@TestDataPath("\$CONTENT_ROOT/../testData/inspections/PyMissingConstructorInspection")
class PyMissingConstructorTest {

  @FolderTest
  fun allTests(file: PsiFile, fixture: CodeInsightTestFixture) {
    fixture.doTestByFile(file)
  }
}
