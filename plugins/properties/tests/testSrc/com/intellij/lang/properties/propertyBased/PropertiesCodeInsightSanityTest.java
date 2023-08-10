// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.propertyBased;

import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.lang.properties.PropertiesLanguage;
import com.intellij.openapi.application.PathManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.propertyBased.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;

import java.util.function.Function;
import java.util.function.Supplier;

@SkipSlowTestLocally
public class PropertiesCodeInsightSanityTest extends LightJavaCodeInsightFixtureTestCase {
  public void testIncrementalHighlighterUpdate() {
    PropertyChecker.checkScenarios(actionsOnPropertiesFiles(CheckHighlighterConsistency.randomEditsWithHighlighterChecks));
  }

  public void testReparse() {
    PropertyChecker.checkScenarios(actionsOnPropertiesFiles(MadTestingUtil::randomEditsWithReparseChecks));
  }

  public void testRandomActivity() {
    MadTestingUtil.enableAllInspections(getProject(), PropertiesLanguage.INSTANCE);
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
    PropertyChecker.checkScenarios(actionsOnPropertiesFiles(fileActions));
  }

  @NotNull
  private Supplier<MadTestingAction> actionsOnPropertiesFiles(Function<? super PsiFile, ? extends Generator<? extends MadTestingAction>> fileActions) {
    return MadTestingUtil.actionsOnFileContents(myFixture, PathManager.getHomePath(),
                                                f -> f.getName().endsWith(PropertiesFileType.DOT_DEFAULT_EXTENSION),
                                                fileActions);
  }
}
