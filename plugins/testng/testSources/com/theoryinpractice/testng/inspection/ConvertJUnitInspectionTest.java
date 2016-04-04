/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.PluginPathManager;

/**
 * @author Dmitry Batkovich
 */
public class ConvertJUnitInspectionTest extends BaseTestNGInspectionsTest {
  @Override
  protected LocalInspectionTool getEnabledTool() {
    return new JUnitConvertTool();
  }

  @Override
  protected String getActionName() {
    return JUnitConvertTool.QUICKFIX_NAME;
  }

  public void testClass() {
    doTest();
  }

  public void testFail() {
    doTest();
  }

  public void testUnaryAssertions() {
    doTest();
  }

  public void testBinaryAssertions() {
    doTest();
  }

  public void testIncompatible() {
    doTest();
  }

  public void testDelta() {
    doTest();
  }

  @Override
  protected String getBasePath() {
    return PluginPathManager.getPluginHomePathRelative("testng") + "/testData/junit";
  }

}
