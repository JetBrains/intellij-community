// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.yaml;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.testFramework.propertyBased.CheckHighlighterConsistency;
import com.intellij.testFramework.propertyBased.MadTestingAction;
import com.intellij.testFramework.propertyBased.MadTestingUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;

import java.util.function.Function;
import java.util.function.Supplier;

@SkipSlowTestLocally
public class YAMLCodeInsightSanityTest extends BasePlatformTestCase {

  public void testIncrementalHighlighterUpdate() {
    PropertyChecker.checkScenarios(actionsOnYamlFiles(CheckHighlighterConsistency.randomEditsWithHighlighterChecks));
  }

  public void testReparse() {
    PropertyChecker.checkScenarios(actionsOnYamlFiles(MadTestingUtil::randomEditsWithReparseChecks));
  }

  @NotNull
  private Supplier<MadTestingAction> actionsOnYamlFiles(Function<? super PsiFile, ? extends Generator<? extends MadTestingAction>> fileActions) {
    return MadTestingUtil.actionsOnFileContents(myFixture,
                                                PathManager.getHomePath(),
                                                f -> FileTypeManager.getInstance().getFileTypeByFileName(f.getName()) == YAMLFileType.YML,
                                                fileActions);
  }
}
