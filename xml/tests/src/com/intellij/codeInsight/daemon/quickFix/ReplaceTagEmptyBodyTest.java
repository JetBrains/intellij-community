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
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.xml.util.CheckTagEmptyBodyInspection;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class ReplaceTagEmptyBodyTest extends LightQuickFixParameterizedTestCase {

  public void test() {
    doAllTests();
  }

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {
      new CheckTagEmptyBodyInspection()
    };
  }

  @Override
  protected String getBasePath() {
    return "/quickFix/replaceTagEmptyBodyWithEmptyEnd";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";
  }
}