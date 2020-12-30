// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.propertyBased;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.propertyBased.CheckHighlighterConsistency;
import com.intellij.testFramework.propertyBased.MadTestingAction;
import com.intellij.testFramework.propertyBased.MadTestingUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;

import java.util.function.Function;
import java.util.function.Supplier;

@SkipSlowTestLocally
public class XmlCodeInsightSanityTest extends LightJavaCodeInsightFixtureTestCase {

  private static final boolean ENABLED = false;

  @Override
  protected boolean shouldRunTest() {
    return ENABLED && super.shouldRunTest();
  }

  public void testIncrementalHighlighterUpdate() {
    PropertyChecker.checkScenarios(actionsOnXmlFiles(CheckHighlighterConsistency.randomEditsWithHighlighterChecks));
  }

  public void testReparse() {
    PropertyChecker.checkScenarios(actionsOnXmlFiles(MadTestingUtil::randomEditsWithReparseChecks));
  }

  @NotNull
  private Supplier<MadTestingAction> actionsOnXmlFiles(Function<? super PsiFile, ? extends Generator<? extends MadTestingAction>> fileActions) {
    return MadTestingUtil.actionsOnFileContents(myFixture,
                                                PathManager.getHomePath(),
                                                f -> FileTypeManager.getInstance().getFileTypeByFileName(f.getName()) == XmlFileType.INSTANCE,
                                                fileActions);
  }
}
