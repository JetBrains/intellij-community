// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.intellij.psi.PsiFile;
import com.intellij.python.junit5Tests.framework.FolderTest;
import com.intellij.python.junit5Tests.framework.metaInfo.Repository;
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.jetbrains.python.junit5.framework.annotations.InspectionTest;
import com.jetbrains.python.junit5.framework.annotations.PyCodeInsightTestApplication;
import com.jetbrains.python.junit5.framework.annotations.WithLanguageLevel;
import com.jetbrains.python.junit5.framework.util.CodeInsightFixtureUtilKt;
import com.jetbrains.python.psi.LanguageLevel;

@TestClassInfo(repository = Repository.PY_COMMUNITY)
@TestDataPath("$CONTENT_ROOT/../testData/inspections")
@PyCodeInsightTestApplication
@InspectionTest(inspectionClasses = PyLazyImportInspection.class)
@WithLanguageLevel(level = LanguageLevel.PYTHON315)
public class PyLazyImportInspectionTest {

  @FolderTest
  public void pyLazyImportInspection(PsiFile file, CodeInsightTestFixture myFixture) {
    CodeInsightFixtureUtilKt.doTestByFile(myFixture, file);
  }
}
