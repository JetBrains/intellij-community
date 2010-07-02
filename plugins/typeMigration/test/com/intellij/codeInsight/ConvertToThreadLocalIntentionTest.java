/*
 * User: anna
 * Date: 28-Oct-2009
 */
package com.intellij.codeInsight;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;

public class ConvertToThreadLocalIntentionTest extends LightQuickFixTestCase {
  private LanguageLevel myPrevLanguageLevel;

  protected void setUp() throws Exception {
    super.setUp();

    myPrevLanguageLevel = LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  protected void tearDown() throws Exception {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(myPrevLanguageLevel);
    super.tearDown();
  }

  @Override
  protected boolean shouldBeAvailableAfterExecution() {
    return true;
  }

  protected String getBasePath() {
    return "/intentions/threadLocal";
  }

  @Override
  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/plugins/typeMigration/testData";
  }

  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17("java 1.5");
  }

  public void test() throws Exception {
    doAllTests();
  }
}