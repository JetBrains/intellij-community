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
package com.intellij.psi.formatter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.xml.XmlFileImpl;

/**
 * @author yole
 */
public abstract class XmlFormatterTestBase extends FormatterTestCase {
  protected void checkFormattingDoesNotProduceException(String name) throws Exception {
    String text = loadFile(name + ".xml", null);
    final XmlFileImpl file = (XmlFileImpl)createFile(name + ".xml", text);
    myTextRange = new TextRange(10000, 10001);
    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> performFormatting(file)), "", "");

    myTextRange = new TextRange(1000000, 1000001);
    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> performFormatting(file)), "", "");
    myTextRange = new TextRange(0, text.length());
    CommandProcessor.getInstance().executeCommand(getProject(), () -> ApplicationManager.getApplication().runWriteAction(() -> performFormatting(file)), "", "");
  }

  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + "/xml/tests/testData";
  }
}
