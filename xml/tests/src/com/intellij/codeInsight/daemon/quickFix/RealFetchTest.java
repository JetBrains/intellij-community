// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ExternalResourceManagerExImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
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
    String location = ExternalResourceManager.getInstance().getResourceLocation(url, getProject());
    assertNotSame(url, location);
    assertTrue(location.endsWith("preferences.dtd")); // no ".xml" suffix added
    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManager.getInstance().removeResource(url));
  }

  public void testRelativePath() {
    final String url = "https://community.rti.com/schema/6.0.0/rti_dds_qos_profiles.xsd";
    assertEquals(url, ExternalResourceManager.getInstance().getResourceLocation(url, getProject()));
    myFixture.configureByText(XmlFileType.INSTANCE,
                              "<dds xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:noNamespaceSchemaLocation=\"https://community.rti.com/schema/6.0.0/rti_dds_qos_pr<caret>ofiles.xsd\">\n" +
                              "    <qos_library name=\"MyLibrary\">\n" +
                              "        <qos_profile name=\"MyProfile\" base_name=\"BuiltinQosLib::Baseline.6.0.0\">\n" +
                              "        </qos_profile>\n" +
                              "    </qos_library>\n" +
                              "</dds>");
    IntentionAction intention = myFixture.getAvailableIntention(XmlBundle.message("fetch.external.resource"));
    assertNotNull(intention);
    intention.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
    myFixture.testHighlighting();
    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManager.getInstance().removeResource(url));
  }

  public void testOverwriteFetchDtd() throws Exception {
    final String url = "http://java.sun.com/dtd/preferences.dtd";
    VirtualFile virtualFile = myFixture.getTempDirFixture().createFile("images.dtd", "");
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, virtualFile.getPath(), getTestRootDisposable());

    myFixture.configureByText(XmlFileType.INSTANCE, "<!DOCTYPE preferences SYSTEM \"<error descr=\"Resource registered by this uri is not recognized (Settings | Languages & Frameworks | Schemas and DTDs)\">http://java.sun.com/dtd/prefe<caret>rences.dtd</error>\"><preferences>\n" +
                                                    "  <root type=\"system\"><map/></root>\n" +
                                                    "</preferences>");
    myFixture.testHighlighting();
    IntentionAction intention = myFixture.getAvailableIntention(XmlBundle.message("fetch.external.resource"));
    assertNotNull(intention);
    intention.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
    List<HighlightInfo> infos = myFixture.doHighlighting();
    assertEmpty(infos);
    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManager.getInstance().removeResource(url));
  }
}
