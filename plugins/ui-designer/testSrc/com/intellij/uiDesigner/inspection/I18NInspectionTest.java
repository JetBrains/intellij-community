/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.uiDesigner.inspection;

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.i18n.I18nInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.InspectionTestCase;
import com.intellij.testFramework.InspectionsKt;
import com.intellij.uiDesigner.i18n.I18nFormInspection;

public class I18NInspectionTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("ui-designer") + "/testData/inspection";
  }

  public void testFormTabbedPaneTitle() {
    LocalInspectionToolWrapper wrapper = new LocalInspectionToolWrapper(new I18nFormInspection());
    InspectionsKt.enableInspectionTool(getProject(), wrapper, getTestRootDisposable());
    doTest("i18n/" + getTestName(true), new LocalInspectionToolWrapper(new I18nInspection()), "java 1.4", false, false,
           wrapper);
  }
}
