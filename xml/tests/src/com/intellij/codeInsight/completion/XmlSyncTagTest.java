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
package com.intellij.codeInsight.completion;

import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;

/**
 * @author Dennis.Ushakov
 */
public abstract class XmlSyncTagTest extends LightPlatformCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.setCaresAboutInjection(false);
  }

  protected void doTest(final String text, final String toType, final String result) {
    doTest(XmlFileType.INSTANCE, text, toType, result);
  }

  protected void doTest(final FileType fileType, final String text, final String toType, final String result) {
    myFixture.configureByText(fileType, text);
    type(toType);
    myFixture.checkResult(result);
  }

  protected void type(String toType) {
    myFixture.type(toType);
  }

  protected void doTestCompletion(final String text, final String toType, final String result) {
    doTestCompletion(XmlFileType.INSTANCE, text, toType, result);
  }

  protected void doTestCompletion(final FileType fileType,
                                  final String text,
                                  final String toType,
                                  final String result) {
    myFixture.configureByText(fileType, text);
    myFixture.completeBasic();
    if (toType != null) myFixture.type(toType);
    myFixture.checkResult(result);
  }
}
