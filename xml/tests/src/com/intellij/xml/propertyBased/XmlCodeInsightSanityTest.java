// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.propertyBased;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.SkipSlowTestLocally;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import com.intellij.testFramework.propertyBased.CheckHighlighterConsistency;
import com.intellij.testFramework.propertyBased.MadTestingAction;
import com.intellij.testFramework.propertyBased.MadTestingUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jetCheck.Generator;
import org.jetbrains.jetCheck.PropertyChecker;

import java.io.File;
import java.util.function.Function;
import java.util.function.Supplier;

@SkipSlowTestLocally
public class XmlCodeInsightSanityTest extends LightCodeInsightFixtureTestCase {

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
  private Supplier<MadTestingAction> actionsOnXmlFiles(Function<PsiFile, Generator<? extends MadTestingAction>> fileActions) {
    String[] extensions = FileTypeManager.getInstance().getAssociatedExtensions(XmlFileType.INSTANCE);

    return MadTestingUtil.actionsOnFileContents(myFixture,
                                                PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') +
                                                "/xml/tests/testData", // PathManager.getHomePath()
                                                f -> {
                                                  String name = f.getName();
                                                  for (String extension: extensions) {
                                                    if (name.endsWith("." + extension)) {
                                                      System.out.println(f.getPath());
                                                      return true;
                                                    }
                                                  }
                                                  return false;
                                                }, fileActions);
  }
}
