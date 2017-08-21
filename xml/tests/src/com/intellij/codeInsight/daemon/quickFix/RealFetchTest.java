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

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase;
import com.intellij.xml.XmlBundle;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class RealFetchTest extends LightPlatformCodeInsightFixtureTestCase {

  public void testFetchDtd() {

    final String url = "http://java.sun.com/dtd/preferences.dtd";
    assertEquals(url, ExternalResourceManager.getInstance().getResourceLocation(url, getProject()));
    myFixture.configureByText(XmlFileType.INSTANCE, "<!DOCTYPE images SYSTEM \"http://java.sun.com/dtd/prefer<caret>ences.dtd\">");
    IntentionAction intention = myFixture.getAvailableIntention(XmlBundle.message("fetch.external.resource"));
    assertNotNull(intention);
    intention.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
    assertNotSame(url, ExternalResourceManager.getInstance().getResourceLocation(url, getProject()));
    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManager.getInstance().removeResource(url));
  }

  public void testOverwriteFetchDtd() throws Exception {

    final String url = "http://java.sun.com/dtd/preferences.dtd";
    VirtualFile virtualFile = myFixture.getTempDirFixture().createFile("images.dtd", "");
    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManager.getInstance().addResource(url, virtualFile.getPath()));

    myFixture.configureByText(XmlFileType.INSTANCE, "<!DOCTYPE images SYSTEM \"<error descr=\"Resource registered by this uri is not recognized (Settings | Languages & Frameworks | Schemas and DTDs)\">http://java.sun.com/dtd/prefer<caret>ences.dtd</error>\"><images> </images>");
    myFixture.testHighlighting();
    IntentionAction intention = myFixture.getAvailableIntention(XmlBundle.message("fetch.external.resource"));
    assertNotNull(intention);
    intention.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
    List<HighlightInfo> infos = myFixture.doHighlighting();
    assertEmpty(infos);
    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManager.getInstance().removeResource(url));
  }
}
