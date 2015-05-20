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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.xml.XmlBundle;

/**
 * @author Dmitry Avdeev
 */
public class RealFetchTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testFetchDtd() throws Exception {

    final String url = "http://helpserver.labs.intellij.net/help/images.dtd";
    assertEquals(url, ExternalResourceManager.getInstance().getResourceLocation(url, getProject()));
    myFixture.configureByText(XmlFileType.INSTANCE, "<!DOCTYPE images SYSTEM \"http://helpserver.labs.intellij.net/help/ima<caret>ges.dtd\">");
    IntentionAction intention = myFixture.getAvailableIntention(XmlBundle.message("fetch.external.resource"));
    assertNotNull(intention);
    intention.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
    assertNotSame(url, ExternalResourceManager.getInstance().getResourceLocation(url, getProject()));
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        ExternalResourceManager.getInstance().removeResource(url);
      }
    });
  }

  @Override
  protected boolean isWriteActionRequired() {
    return false;
  }
}
