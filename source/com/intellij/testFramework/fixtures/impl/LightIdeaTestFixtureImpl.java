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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;

/**
 * @author mike
 */
class LightIdeaTestFixtureImpl extends BaseFixture implements IdeaProjectTestFixture {
  public void setUp() throws Exception {
    super.setUp();

    LightIdeaTestCase.initApplication(new MyDataProvider());
    LightIdeaTestCase.doSetup(JavaSdkImpl.getMockJdk("java 1.4"), new LocalInspectionTool[0], new HashMap<String, LocalInspectionTool>(), null);
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
