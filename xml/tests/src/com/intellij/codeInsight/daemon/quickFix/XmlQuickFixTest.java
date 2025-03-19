// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.xml.analysis.XmlAnalysisBundle;
import org.intellij.plugins.intelliLang.inject.LanguageInjectionSupport;
import org.intellij.plugins.intelliLang.inject.TemporaryPlacesRegistry;

public class XmlQuickFixTest extends BasePlatformTestCase {

  public void testEscapeAmpersandInInjected() {
    XmlFile file = (XmlFile)myFixture.configureByText(XmlFileType.INSTANCE, "<a b=\" &<caret>\"/>");
    PsiElement element = file.getRootTag().getAttribute("b").getValueElement();
    LanguageInjectionSupport support = TemporaryPlacesRegistry.getInstance(getProject()).getLanguageInjectionSupport();
    assertTrue(support.addInjectionInPlace(PlainTextLanguage.INSTANCE, (PsiLanguageInjectionHost)element));
    IntentionAction intention = myFixture.getAvailableIntention(XmlAnalysisBundle.message("xml.quickfix.escape.character", "&", "&amp;"));
    assertNotNull(intention);
    ShowIntentionActionsHandler.chooseActionAndInvoke(file, myFixture.getEditor(), intention, "");
    myFixture.checkResult("<a b=\" &amp;\"/>");
  }
}
