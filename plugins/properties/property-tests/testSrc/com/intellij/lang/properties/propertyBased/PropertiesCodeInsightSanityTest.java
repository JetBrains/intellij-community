// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.propertyBased;

import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.openapi.application.PathManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.testFramework.propertyBased.CheckHighlighterConsistency;
import com.intellij.testFramework.propertyBased.DeleteRange;
import com.intellij.testFramework.propertyBased.IntentionPolicy;
import com.intellij.testFramework.propertyBased.InvokeIntention;
import com.intellij.testFramework.propertyBased.MadTestingAction;
import com.intellij.testFramework.propertyBased.MadTestingUtil;
import com.intellij.testFramework.propertyBased.StripTestDataMarkup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;
import org.junit.jupiter.api.Test;

import java.util.function.Function;
import java.util.function.Supplier;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@SkipSlowTestLocally
@TestApplication
public class PropertiesCodeInsightSanityTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testIncrementalHighlighterUpdate() {
    EdtTestUtil.runInEdtAndWait(() -> PropertyChecker.checkScenarios(actionsOnPropertiesFiles(CheckHighlighterConsistency.randomEditsWithHighlighterChecks)));
  }

  @Test
  public void testReparse() {
    EdtTestUtil.runInEdtAndWait(() -> PropertyChecker.checkScenarios(actionsOnPropertiesFiles(MadTestingUtil::randomEditsWithReparseChecks)));
  }

  @Test
  public void testRandomActivity() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    MadTestingUtil.enableAllInspections(myFixture.getProject(), PropertiesLanguage.INSTANCE);
    Function<PsiFile, Generator<? extends MadTestingAction>> fileActions =
      file -> Generator.sampledFrom(new InvokeIntention(file, new IntentionPolicy() {
                                      @Override
                                      protected boolean shouldSkipIntention(@NotNull String actionText) {
                                        return actionText.equals("Sort resource bundle files") || // todo IDEA-194044
                                               actionText.startsWith("Suppress for"); // todo IDEA-193419, IDEA-193420
                                      }
                                    }),
                                    new StripTestDataMarkup(file),
                                    new DeleteRange(file));
    EdtTestUtil.runInEdtAndWait(() -> PropertyChecker.checkScenarios(actionsOnPropertiesFiles(fileActions)));
  }

  @NotNull
  private Supplier<MadTestingAction> actionsOnPropertiesFiles(Function<? super PsiFile, ? extends Generator<? extends MadTestingAction>> fileActions) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    return MadTestingUtil.actionsOnFileContents(myFixture, PathManager.getHomePath(),
                                                f -> f.getName().endsWith(PropertiesFileType.DOT_DEFAULT_EXTENSION),
                                                fileActions);
  }
}
