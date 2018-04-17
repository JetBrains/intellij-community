// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.properties.propertyBased;

import com.intellij.lang.properties.PropertiesFileType;
import com.intellij.openapi.application.PathManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.testFramework.propertyBased.CheckHighlighterConsistency;
import com.intellij.testFramework.propertyBased.MadTestingAction;
import com.intellij.testFramework.propertyBased.MadTestingUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;

import java.util.function.Function;
import java.util.function.Supplier;

@SkipSlowTestLocally
public class PropertiesCodeInsightSanityTest extends LightCodeInsightFixtureTestCase {

  public void testIncrementalHighlighterUpdate() {
    PropertyChecker.checkScenarios(actionsOnPropertiesFiles(CheckHighlighterConsistency.randomEditsWithHighlighterChecks));
  }

  public void testReparse() {
    PropertyChecker.checkScenarios(actionsOnPropertiesFiles(MadTestingUtil::randomEditsWithReparseChecks));
  }

  @NotNull
  private Supplier<MadTestingAction> actionsOnPropertiesFiles(Function<PsiFile, Generator<? extends MadTestingAction>> fileActions) {
    return MadTestingUtil.actionsOnFileContents(myFixture, PathManager.getHomePath(),
                                                f -> f.getName().endsWith(PropertiesFileType.DOT_DEFAULT_EXTENSION), fileActions);
  }
}
