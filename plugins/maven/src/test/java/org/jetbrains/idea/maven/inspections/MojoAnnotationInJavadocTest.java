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
package org.jetbrains.idea.maven.inspections;

import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.testFramework.InspectionTestCase;

public class MojoAnnotationInJavadocTest extends InspectionTestCase {

  @Override
  protected String getTestDataPath() {
    return PluginPathManager.getPluginHomePath("maven") + "/src/test/data/inspections";
  }

  public void testJavadocMojoValidTags() throws Exception {
    doTest(getTestName(true), new JavaDocLocalInspection());
  }
}
