// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.lomboktest;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixture;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection.ConversionStrategy;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import de.plushnikov.intellij.plugin.LombokTestUtil;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;

import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixFixture;
import static com.intellij.codeInsight.daemon.quickFix.LightQuickFixFixtureKt.lightQuickFixTestFiles;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class LombokClassCanBeRecordInspectionTest {
  private static final String TEST_DATA_PATH = LombokTestUtil.getTestDataPath() + "/inspection/classCanBeRecord";
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LombokTestUtil.LOMBOK_JAVA21_DESCRIPTOR);
  private final TestFixture<LightQuickFixFixture> quickFixFixture = lightQuickFixFixture(
    projectFixture, TEST_DATA_PATH, () -> List.of(new ClassCanBeRecordInspection(ConversionStrategy.DO_NOT_SUGGEST, true)));

  static List<String> files() {
    return lightQuickFixTestFiles(TEST_DATA_PATH);
  }

  @ParameterizedTest(name = "{argumentSetNameOrArgumentsWithNames}")
  @MethodSource("files")
  void parameterized(String fileName) {
    LightQuickFixFixture quickFix = quickFixFixture.get();
    String option = StringUtil.substringAfter(fileName, "__");
    if (option != null && option.startsWith("ignoreConflicts")) {
      // TODO(bartekpacia): It'd be good to test that specific conflicts appear. See IDEA-370463
      BaseRefactoringProcessor.ConflictsInTestsException.withIgnoredConflicts(() -> quickFix.doTest(fileName));
    }
    else {
      quickFix.doTest(fileName);
    }
  }
}
