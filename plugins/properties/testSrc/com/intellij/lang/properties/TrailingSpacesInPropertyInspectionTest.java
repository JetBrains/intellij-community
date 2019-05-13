/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInspection.TrailingSpacesInPropertyInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class TrailingSpacesInPropertyInspectionTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("properties") + "/testData";
  }

  public void testSimple() {
    final TrailingSpacesInPropertyInspection inspection = new TrailingSpacesInPropertyInspection();
    myFixture.enableInspections(inspection);
    myFixture.configureByFile("/propertiesFile/highlighting/trailingSpaces.properties");
    myFixture.checkHighlighting(true, false, true);
    myFixture.disableInspections(inspection);
  }

  public void testOnlyNonVisible() {
    final TrailingSpacesInPropertyInspection inspection = new TrailingSpacesInPropertyInspection();
    inspection.myIgnoreVisibleSpaces = true;
    myFixture.enableInspections(inspection);
    VirtualFile file = myFixture.copyFileToProject("/propertiesFile/highlighting/trailingSpaces2.properties");
    myFixture.configureFromExistingVirtualFile(file);
    myFixture.checkHighlighting(true, false, true);
    myFixture.disableInspections(inspection);
  }
}