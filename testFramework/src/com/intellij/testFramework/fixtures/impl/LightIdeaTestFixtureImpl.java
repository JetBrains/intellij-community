/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Factory;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author mike
 */
class LightIdeaTestFixtureImpl extends BaseFixture implements IdeaProjectTestFixture {
  private final Factory<Sdk> mySdkFactory;
  protected final ModuleType myModuleType;

  public LightIdeaTestFixtureImpl(@Nullable final Factory<Sdk> sdk, final ModuleType moduleType) {
    mySdkFactory = sdk;
    myModuleType = moduleType;
  }

  public void setUp() throws Exception {
    super.setUp();

    LightPlatformTestCase.initApplication(new MyDataProvider());
    final Sdk sdk = mySdkFactory == null ? null : mySdkFactory.create();
    LightPlatformTestCase.doSetup(sdk, myModuleType, new LocalInspectionTool[0], null);
    storeSettings();
  }

  private class MyDataProvider implements DataProvider {
    @Nullable
    public Object getData(@NonNls String dataId) {
      if (dataId.equals(DataConstants.PROJECT)) {
        return getProject();
      }
      else if (dataId.equals(DataConstants.EDITOR) || dataId.equals(OpenFileDescriptor.NAVIGATE_IN_EDITOR.getName())) {
        return FileEditorManager.getInstance(getProject()).getSelectedTextEditor();
      }
      else {
        return null;
      }
    }
  }


  public void tearDown() throws Exception {
    CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    checkForSettingsDamage();
    LightPlatformTestCase.doTearDown();
    super.tearDown();
  }

  public Project getProject() {
    return LightPlatformTestCase.getProject();
  }

  @Override
  protected CodeStyleSettings getCurrentCodeStyleSettings() {
    return CodeStyleSettingsManager.getSettings(getProject());
  }

  public Module getModule() {
    return LightPlatformTestCase.getModule();
  }
}
