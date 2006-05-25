/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures.impl;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.testFramework.LightIdeaTestCase;
import com.intellij.testFramework.fixtures.IdeaTestFixture;

import java.util.HashMap;

/**
 * @author mike
 */
public class LightIdeaTestFixtureImpl implements IdeaTestFixture {
  public void setUp() throws Exception {
    LightIdeaTestCase.initApplication(null);
    LightIdeaTestCase.doSetup(JavaSdkImpl.getMockJdk15("50"), new LocalInspectionTool[0], new HashMap<String, LocalInspectionTool>(), null);
  }

  public void tearDown() throws Exception {
    LightIdeaTestCase.doTearDown();
  }

  public Project getProject() {
    return LightIdeaTestCase.getProject();
  }

  public Module getModule() {
    return LightIdeaTestCase.getModule();
  }
}
