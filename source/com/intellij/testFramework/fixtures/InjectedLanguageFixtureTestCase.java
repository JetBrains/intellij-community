/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.testFramework.fixtures;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.application.Result;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.Function;
import com.intellij.psi.PsiFile;

import java.util.Arrays;

/**
 * @author peter
 */
public class InjectedLanguageFixtureTestCase extends CodeInsightFixtureTestCase {
  protected void checkCompletionVariants(final FileType fileType, final String text, final String... strings) throws Throwable {
    myFixture.configureByText(fileType, text.replaceAll("\\|", "<caret>"));
    tuneCompletionFile(myFixture.getFile());
    final LookupElement[] elements = myFixture.completeBasic();
    assertNotNull(elements);
    myFixture.checkResult(text.replaceAll("\\|", "<caret>"));

    UsefulTestCase.assertSameElements(ContainerUtil.map(elements, new Function<LookupElement, String>() {
      public String fun(final LookupElement lookupItem) {
        return lookupItem.getLookupString();
      }
    }), strings);
  }

  protected void assertNoVariants(final FileType fileType, final String text) throws Throwable {
    checkCompleted(fileType, text, text);
  }

  protected void checkCompleted(final FileType fileType, final String text, final String resultText) throws Throwable {
    myFixture.configureByText(fileType, text.replaceAll("\\|", "<caret>"));
    tuneCompletionFile(myFixture.getFile());
    final LookupElement[] elements = myFixture.completeBasic();
    if (elements != null && elements.length == 1) {
      new WriteCommandAction(getProject()) {
        protected void run(Result result) throws Throwable {
          ((LookupImpl)LookupManager.getInstance(getProject()).getActiveLookup()).finishLookup(Lookup.NORMAL_SELECT_CHAR);
        }
      }.execute();
    }
    else if (elements != null && elements.length > 0) {
      fail(Arrays.toString(elements));
    }
    myFixture.checkResult(resultText.replaceAll("\\|", "<caret>"));
  }

  protected void tuneCompletionFile(PsiFile file) {
  }

}
