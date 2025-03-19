// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.emmet;

import com.intellij.codeInsight.template.CustomTemplateCallback;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateSettings;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.EditorTestUtil;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;
import com.intellij.util.ui.UIUtil;

import static com.intellij.codeInsight.template.emmet.ZenCodingTemplate.doWrap;

public abstract class EmmetAbbreviationTestCase extends LightPlatformCodeInsightTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
  }

  public void expand(String text) {
    expand(text, getExtension());
  }

  public void expandAndCheck(String sourceData, String expectedData) {
    expand(sourceData, getExtension());

    checkResultByText(expectedData);
  }

  protected void templateWrap(String selectedText, TemplateImpl template, String expectedResult) {
    configureFromFileText("text." + getExtension(), selectSourceData(selectedText));
    ApplicationManager.getApplication().runWriteAction(() -> TemplateManager.getInstance(getProject()).startTemplate(getEditor(), selectedText, template));

    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    UIUtil.dispatchAllInvocationEvents();

    checkResultByText(expectedResult);
  }

  abstract protected String getExtension();

  public void expand(String text, String extension) {
    configureFromFileText("test." + extension, text);
    if (!text.contains(EditorTestUtil.CARET_TAG)) {
      end();
    }
    ApplicationManager.getApplication().runWriteAction(() -> {
      TemplateManager.getInstance(getProject()).startTemplate(getEditor(), TemplateSettings.TAB_CHAR);
    });

    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    UIUtil.dispatchAllInvocationEvents();
  }

  public void emmetWrap(String sourceData, String emmetExpression, String expectedData) {
    configureFromFileText("test." + getExtension(), selectSourceData(sourceData));
    final CustomTemplateCallback callback = new CustomTemplateCallback(getEditor(), getFile());
    doWrap(emmetExpression, callback);
    TemplateState state;
    while ((state = TemplateManagerImpl.getTemplateState(getEditor())) != null) {
      state.nextTab();
    }
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    UIUtil.dispatchAllInvocationEvents();

    checkResultByText(expectedData);
  }

  public void emmetUpdateTag(String sourceData, String emmetExpression, String expectedData) {
    configureFromFileText("test." + getExtension(), sourceData);
    EmmetAbbreviationBalloon.setTestingAbbreviation(emmetExpression, getTestRootDisposable());
    executeAction(IdeActions.ACTION_UPDATE_TAG_WITH_EMMET);

    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    UIUtil.dispatchAllInvocationEvents();

    checkResultByText(expectedData);
  }

  private static String selectSourceData(String sourceData) {
    return sourceData.contains(EditorTestUtil.SELECTION_START_TAG)
                 ? sourceData
                 : EditorTestUtil.SELECTION_START_TAG + sourceData + EditorTestUtil.SELECTION_END_TAG;
  }
}
