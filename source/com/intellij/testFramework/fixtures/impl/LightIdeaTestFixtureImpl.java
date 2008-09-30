/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

import java.util.HashMap;

/**
 * @author mike
 */
class LightIdeaTestFixtureImpl extends BaseFixture implements IdeaProjectTestFixture {
  public void setUp() throws Exception {
    super.setUp();

    LightIdeaTestCase.initApplication(null);
    LightIdeaTestCase.doSetup(JavaSdkImpl.getMockJdk15("50"), new LocalInspectionTool[0], new HashMap<String, LocalInspectionTool>(), null);
    storeSettings();
  }

  public void tearDown() throws Exception {
    CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    checkForSettingsDamage();
    LightIdeaTestCase.doTearDown();
    super.tearDown();
  }

  public Project getProject() {
    return LightIdeaTestCase.getProject();
  }

  @Override
  protected CodeStyleSettings getCurrentCodeStyleSettings() {
    return CodeStyleSettingsManager.getSettings(getProject());
  }

  public Module getModule() {
    return LightIdeaTestCase.getModule();
  }
}
