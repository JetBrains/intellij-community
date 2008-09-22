/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.testFramework.fixtures;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.Arrays;

/**
 * @author peter
 */
public abstract class CodeInsightFixtureTestCase extends UsefulTestCase{
  protected CodeInsightTestFixture myFixture;
  protected Module myModule;

  protected void setUp() throws Exception {
    super.setUp();

    final TestFixtureBuilder<IdeaProjectTestFixture> projectBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder();
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(projectBuilder.getFixture());
    final JavaModuleFixtureBuilder moduleFixtureBuilder = projectBuilder.addModule(JavaModuleFixtureBuilder.class);
    moduleFixtureBuilder.addSourceContentRoot(myFixture.getTempDirPath());
    tuneFixture(moduleFixtureBuilder);    

    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
    myModule = moduleFixtureBuilder.getFixture().getModule();
  }

  /**
   * Return relative path to the test data.
   *
   * @return relative path to the test data.
   */
  @NonNls
  protected String getBasePath() {
    return "";
  }

  /**
   * Return absolute path to the test data. Not intended to be overrided.
   *
   * @return absolute path to the test data.
   */
  @NonNls
  protected final String getTestDataPath() {
    return PathManager.getHomePath().replace(File.separatorChar, '/') + getBasePath();
  }

  protected void tuneFixture(final JavaModuleFixtureBuilder moduleBuilder) {}

  protected void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
    super.tearDown();
  }

  protected Project getProject() {
    return myFixture.getProject();
  }

  protected void tuneCompletionFile(PsiFile file) {
  }

  protected void checkCompletionVariants(final FileType fileType, final String text, final String... strings) throws Throwable {
    myFixture.configureByText(fileType, text.replaceAll("\\|", "<caret>"));
    tuneCompletionFile(myFixture.getFile());
    final LookupElement[] elements = myFixture.completeBasic();
    assertNotNull(elements);
    myFixture.checkResult(text.replaceAll("\\|", "<caret>"));

    UsefulTestCase.assertSameElements(ContainerUtil.map(elements, new Function<LookupElement, String>() {
      public String fun(final LookupElement lookupItem) {
        return lookupItem.getLookupString();
      }
    }), strings);
  }

  protected void checkCompleted(final FileType fileType, final String text, final String resultText) throws Throwable {
    myFixture.configureByText(fileType, text.replaceAll("\\|", "<caret>"));
    tuneCompletionFile(myFixture.getFile());
    final LookupElement[] elements = myFixture.completeBasic();
    if (elements != null && elements.length == 1) {
      new WriteCommandAction(getProject()) {
        protected void run(Result result) throws Throwable {
          ((LookupImpl)LookupManager.getInstance(getProject()).getActiveLookup()).finishLookup(Lookup.NORMAL_SELECT_CHAR);
        }
      }.execute();
    }
    else if (elements != null && elements.length > 0) {
      fail(Arrays.toString(elements));
    }
    myFixture.checkResult(resultText.replaceAll("\\|", "<caret>"));
  }

  protected void assertNoVariants(final FileType fileType, final String text) throws Throwable {
    checkCompleted(fileType, text, text);
  }

  protected PsiManager getPsiManager() {
    return PsiManager.getInstance(getProject());
  }

  public PsiElementFactory getElementFactory() {
    return JavaPsiFacade.getInstance(getProject()).getElementFactory();
  }
}
