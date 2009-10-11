/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 04-Jun-2007
 */
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInspection.LocalInspectionTool;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

public class ConvertJavadocInspectionTest extends BaseTestNGInspectionsTest{
  protected String getSourceRoot() {
    return "javadoc2Annotation";
  }

  protected LocalInspectionTool getEnabledTool() {
    return new ConvertJavadocInspection();
  }

  @DataProvider
  public Object[][] data() {
    return new String[][]{new String[]{"1"}, new String[]{"2"}, new String[]{"3"}};
  }

  @Test (dataProvider = "data")
  public void test(String suffix) throws Throwable {
    doTest(suffix);
  }
}