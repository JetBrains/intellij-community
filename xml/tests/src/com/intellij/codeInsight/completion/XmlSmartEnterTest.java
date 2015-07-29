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
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupEvent;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInsight.lookup.impl.LookupManagerImpl;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * @author spleaner
 */
@SuppressWarnings({"ALL"})
public class XmlSmartEnterTest extends LightCodeInsightTestCase {
  private static final String BASE_PATH = "/smartEnter";
  private LookupElement[] myItems;

  public void testSmartFinish1() throws Throwable {
    doCompletionPopupTest(getTestName(false));
  }

  public void testSmartFinish2() throws Throwable {
    boolean old = CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION;
    CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    try {
      doCompletionPopupTest(getTestName(false));
    }
    finally {
      CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = old;
    }
  }

  public void testSmartFinish3() throws Exception {
    doSmartEnterTest("SmartFinish1");
  }

  public void testSmartFinish4() throws Exception {
    doSmartEnterTest("SmartFinish2");
  }

  public void testSmartFinish5() throws Exception {
    doSmartEnterTest("SmartFinish3");
  }

  public void testSmartFinish_IDEADEV_29628() throws Exception {
    doSmartEnterTest("IDEADEV_29628");
  }

  public void testSmartFinishWithWrongAttribute() throws Exception {
    doSmartEnterTest("WrongAttribute");
  }

  public void testOpenAttribute() throws Exception {
    doSmartEnterTest("OpenAttribute");
  }

  public void testComplete1() throws Exception {
    _doTest("Tag.xml", "Tag_after.xml");
  }

  private void doCompletionPopupTest(String baseName) throws Exception {
    _doTestCompletion(baseName + ".xml", baseName + "_after.xml");
  }

  public void testSmartCloseTag() throws Exception {
    configureByFile(BASE_PATH + "/" + "CompleteMissingTag.xml");
    performSmartEnterAction();
    checkResultByFile("", BASE_PATH + "/" + "CompleteMissingTag_after_1.xml", true);
    performSmartEnterAction();
    checkResultByFile("", BASE_PATH + "/" + "CompleteMissingTag_after_2.xml", true);
    performSmartEnterAction();
    checkResultByFile("", BASE_PATH + "/" + "CompleteMissingTag_after_3.xml", true);
    performSmartEnterAction();
    checkResultByFile("", BASE_PATH + "/" + "CompleteMissingTag_after_4.xml", true);
    performSmartEnterAction();
    checkResultByFile("", BASE_PATH + "/" + "CompleteMissingTag_after_5.xml", true);
  }

  public void testSmartCloseTag2() throws Exception {
    _doTest("idea103417_1.xml", "idea103417_1_after.xml");
    _doTest("idea103417_2.xml", "idea103417_2_after.xml");
  }

  private void _doTestCompletion(final String name, final String after_name) throws Exception {
    configureByFile(BASE_PATH + "/" + name);
    performCompletionAction();
    select(Lookup.COMPLETE_STATEMENT_SELECT_CHAR);
    checkResultByFile(BASE_PATH + "/" + after_name);
  }

  private void doSmartEnterTest(String baseName) throws Exception {
    _doTest(baseName + ".xml", baseName + "_after.xml");
  }

  private void _doTest(String filename, String filename_after) throws Exception {
    configureByFile(BASE_PATH + "/" + filename);
    performSmartEnterAction();
    checkResultByFile("", BASE_PATH + "/" + filename_after, true);
  }

  private void performSmartEnterAction() {
    new WriteCommandAction(getProject()) {
      protected void run(@NotNull final Result result) throws Throwable {
        new XmlSmartEnterProcessor().process(getProject(), getEditor(), getFile());
      }
    }.execute();
  }

  private void performCompletionAction() {
    new CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(getProject(), getEditor());
    final LookupImpl lookup = (LookupImpl)LookupManager.getActiveLookup(getEditor());
    myItems = lookup == null ? null : lookup.getItems().toArray(LookupElement.EMPTY_ARRAY);
  }

  private void select(final char c) {
    if (!LookupEvent.isSpecialCompletionChar(c)) {
      type(c);
      return;
    }

    final LookupManagerImpl manager = (LookupManagerImpl) LookupManager.getInstance(getProject());
    final Lookup lookup = manager.getActiveLookup();
    if(lookup != null) {
      manager.forceSelection(c, lookup.getCurrentItem());
    }
  }

  protected String getBasePath() {
    return "";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";
  }
}
