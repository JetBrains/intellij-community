// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.propertyBased;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.testFramework.propertyBased.CheckHighlighterConsistency;
import com.intellij.testFramework.propertyBased.MadTestingAction;
import com.intellij.testFramework.propertyBased.MadTestingUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.util.function.Function;
import java.util.function.Supplier;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@SkipSlowTestLocally
@EnabledIf("isEnabled")
@TestApplication
public class XmlCodeInsightSanityTest {

  private static final boolean ENABLED = false;

  static boolean isEnabled() {
    return ENABLED;
  }

  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testIncrementalHighlighterUpdate() {
    EdtTestUtil.runInEdtAndWait(() -> PropertyChecker.checkScenarios(actionsOnXmlFiles(CheckHighlighterConsistency.randomEditsWithHighlighterChecks)));
  }

  @Test
  public void testReparse() {
    EdtTestUtil.runInEdtAndWait(() -> PropertyChecker.checkScenarios(actionsOnXmlFiles(MadTestingUtil::randomEditsWithReparseChecks)));
  }

  @NotNull
  private Supplier<MadTestingAction> actionsOnXmlFiles(Function<? super PsiFile, ? extends Generator<? extends MadTestingAction>> fileActions) {
    return MadTestingUtil.actionsOnFileContents(codeInsightFixture.get(),
                                                PathManager.getHomePath(),
                                                f -> FileTypeManager.getInstance().getFileTypeByFileName(f.getName()) == XmlFileType.INSTANCE,
                                                fileActions);
  }
}
