// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.impl.source.xml.XmlFileImpl;


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
