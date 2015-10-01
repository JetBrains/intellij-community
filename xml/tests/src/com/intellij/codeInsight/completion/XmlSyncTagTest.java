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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
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

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
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
    for (int i = 0; i < toType.length(); i++) {
      final char c = toType.charAt(i);
      CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              myFixture.type(c);
            }
          });
        }
      }, "Typing", DocCommandGroupId.noneGroupId(myFixture.getEditor().getDocument()), myFixture.getEditor().getDocument());
    }
  }

  protected void doTestCompletion(final String text, final String toType, final String result) {
    doTestCompletion(XmlFileType.INSTANCE, text, toType, result);
  }

  protected void doTestCompletion(final FileType fileType,
                                  final String text,
                                  final String toType,
                                  final String result) {
    myFixture.configureByText(fileType, text);
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            myFixture.completeBasic();
            if (toType != null) myFixture.type(toType);
          }
        });
      }
    }, "Typing", DocCommandGroupId.noneGroupId(myFixture.getEditor().getDocument()), myFixture.getEditor().getDocument());
    myFixture.checkResult(result);
  }

  @Override
  protected boolean isWriteActionRequired() {
    return false;
  }
}
