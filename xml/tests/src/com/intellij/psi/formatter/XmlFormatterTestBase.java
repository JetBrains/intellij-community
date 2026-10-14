// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter;

import com.intellij.application.options.CodeStyle;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.impl.source.xml.XmlFileImpl;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;

import java.io.File;
import java.io.IOException;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

public abstract class XmlFormatterTestBase {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  /**
   * The range to format in {@link #checkDocument}, as the {@code myTextRange} field of {@link FormatterTestCase}.
   * When it is {@code null}, the full file is formatted.
   */
  protected TextRange myTextRange;

  private Project project;
  private String testMethodName;

  @BeforeEach
  void setUpXmlFormatterTestBase(TestInfo testInfo) {
    project = codeInsightFixture.get().getProject();
    FormatterTestUtil.assertNoProjectCodeStyle(project);
    testMethodName = testInfo.getTestMethod().orElseThrow().getName();
  }

  protected abstract String getBasePath();

  protected abstract String getFileExtension();

  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/xml/tests/testData";
  }

  protected final @NotNull Project getProject() {
    return project;
  }

  protected final @NotNull Disposable getTestRootDisposable() {
    return codeInsightFixture.get().getTestRootDisposable();
  }

  protected final @NotNull String getTestName(boolean lowercaseFirstLetter) {
    return PlatformTestUtil.getTestName(testMethodName, lowercaseFirstLetter);
  }

  protected final @NotNull String getTestDataDir() {
    return getTestDataPath() + File.separatorChar + getBasePath();
  }

  protected final @NotNull CodeStyleSettings getSettings() {
    return CodeStyle.getSettings(project);
  }

  protected final @NotNull CommonCodeStyleSettings getSettings(@NotNull Language language) {
    return getSettings().getCommonSettings(language);
  }

  /**
   * Returns the file type of the files that {@link #doTextTest} creates, as {@code FormatterTestCase.getFileType} did.
   */
  protected FileType getFileType(String fileName) {
    return FileTypeManager.getInstance().getFileTypeByFileName(fileName);
  }

  protected final @NotNull String loadFile(String name, String resultNumber) throws IOException {
    return FormatterTestUtil.loadFile(getTestDataDir(), name, resultNumber);
  }

  /**
   * Creates a file with the file type of {@code fileName}, as {@code LightPlatformTestCase.createFile} did.
   */
  protected final @NotNull PsiFile createFile(@NotNull String fileName, @NotNull String text) {
    return FormatterTestUtil.createFile(project, fileName, text);
  }

  /**
   * Creates a file with {@link #getFileType}, as {@code FormatterTestCase.createFileFromText} did.
   */
  protected final @NotNull PsiFile createFileFromText(@NotNull String text, @NotNull String fileName) {
    return FormatterTestUtil.createFile(project, fileName, getFileType(fileName), text);
  }

  protected final void doTest() throws IOException {
    doTest(null);
  }

  protected final void doTest(String resultNumber) throws IOException {
    String testName = getTestName(true);
    String extension = getFileExtension();
    doTextTest(loadFile(testName + "." + extension, null), loadFile(testName + "_after." + extension, resultNumber));
  }

  protected final void doTextTest(String text, String textAfter) {
    PsiFile file = createFileFromText(text, "before." + getFileExtension());
    checkDocument(file, text, textAfter);
  }

  protected void checkDocument(@NotNull PsiFile file, @NotNull String text, @NotNull String textAfter) {
    FormatterTestUtil.checkDocument(project, file, text, textAfter, myTextRange);
  }

  protected final void doSanityTest(boolean formatWithPsi) throws IOException {
    FormatterTestUtil.doSanityTest(project, getTestDataDir(), getFileExtension(), formatWithPsi);
  }

  protected void checkFormattingDoesNotProduceException(String name) throws Exception {
    String text = loadFile(name + ".xml", null);
    final XmlFileImpl file = (XmlFileImpl)createFile(name + ".xml", text);
    FormatterTestUtil.reformat(project, file, new TextRange(10000, 10001));
    FormatterTestUtil.reformat(project, file, new TextRange(1000000, 1000001));
    FormatterTestUtil.reformat(project, file, new TextRange(0, text.length()));
  }
}
