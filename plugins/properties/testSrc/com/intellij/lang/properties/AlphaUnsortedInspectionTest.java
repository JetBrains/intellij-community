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
package com.intellij.lang.properties;

import com.intellij.ExtensionPoints;
import com.intellij.codeInspection.duplicatePropertyInspection.DuplicatePropertyInspection;
import com.intellij.codeInspection.unsorted.AlphaUnsortedPropertiesFileInspection;
import com.intellij.codeInspection.unsorted.AlphaUnsortedPropertiesFileInspectionSuppressor;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.testFramework.InspectionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class AlphaUnsortedInspectionTest extends InspectionTestCase {
  private AlphaUnsortedPropertiesFileInspection myTool;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myTool = new AlphaUnsortedPropertiesFileInspection();
  }

  public void testUnsorted() throws Exception {
    doTest();
  }

  public void testSorted() throws Exception {
    doTest();
  }

  public void testUnsortedSuppressed() throws Exception {
    final ExtensionPoint<AlphaUnsortedPropertiesFileInspectionSuppressor> ep =
      Extensions.getRootArea().getExtensionPoint(AlphaUnsortedPropertiesFileInspectionSuppressor.EP_NAME);
    final AlphaUnsortedPropertiesFileInspectionSuppressor suppressor = new AlphaUnsortedPropertiesFileInspectionSuppressor() {
      @Override
      public boolean suppressInspectionFor(PropertiesFile propertiesFile) {
        return propertiesFile.getName().toLowerCase().contains("suppress");
      }
    };
    try {
      ep.registerExtension(suppressor);
      doTest();
    } finally {
      ep.unregisterExtension(suppressor);
    }
  }

  private void doTest() throws Exception {
    doTest("alphaUnsorted/" + getTestName(true), myTool);
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("properties") + "/testData";
  }

}
