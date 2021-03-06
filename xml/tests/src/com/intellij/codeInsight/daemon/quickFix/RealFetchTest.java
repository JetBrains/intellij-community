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
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlBundle;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class RealFetchTest extends BasePlatformTestCase {

  public void testFetchDtd() {
    final String url = "http://java.sun.com/dtd/preferences.dtd";
    assertEquals(url, ExternalResourceManager.getInstance().getResourceLocation(url, getProject()));
    myFixture.configureByText(XmlFileType.INSTANCE, "<!DOCTYPE images SYSTEM \"http://java.sun.com/dtd/prefer<caret>ences.dtd\">");
    IntentionAction intention = myFixture.getAvailableIntention(XmlBundle.message("xml.intention.fetch.name"));
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
    IntentionAction intention = myFixture.getAvailableIntention(XmlBundle.message("xml.intention.fetch.name"));
    assertNotNull(intention);
    intention.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
    myFixture.testHighlighting();
    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManager.getInstance().removeResource(url));
  }

  public void testNestedRelativePath() {
    final String url = "https://community.rti.com/schema/6.0.0/rti_dds_profiles.xsd";
    assertEquals(url, ExternalResourceManager.getInstance().getResourceLocation(url, getProject()));
    myFixture.configureByText(XmlFileType.INSTANCE,
                              "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>\n" +
                              "<dds xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                              "     xsi:noNamespaceSchemaLocation=\"https://community.rti.com/schema/6.0.0/rti_dd<caret>s_profiles.xsd\">\n" +
                              "\n" +
                              "  <types/>\n" +
                              "  <domain_library name=\"xxx\"/>\n" +
                              "  <domain_participant_library name=\"ffff\"/>\n" +
                              "</dds>");
    IntentionAction intention = myFixture.getAvailableIntention(XmlBundle.message("xml.intention.fetch.name"));
    assertNotNull(intention);
    intention.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());

    myFixture.testHighlighting();

    XmlFile file = (XmlFile)myFixture.getFile();
    XmlAttributeDescriptor descriptor = file.getRootTag().getSubTags()[1].getAttribute("name").getDescriptor();
    PsiFile containingFile = descriptor.getDeclaration().getContainingFile();
    assertNotNull(containingFile.getParent());
    assertEquals("definitions", containingFile.getParent().getName());

    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManager.getInstance().removeResource(url));
  }

  public void testAbsolutePath() {
    String url = "https://csrc.nist.gov/schema/xccdf/1.2/xc<caret>cdf_1.2.xsd";
    myFixture.configureByText(XmlFileType.INSTANCE,
                              "<Benchmark xmlns=\"http://checklists.nist.gov/xccdf/1.2\"\n" +
                              "           xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
                              "           xsi:schemaLocation=\"http://checklists.nist.gov/xccdf/1.2 https://csrc.nist.gov/schema/xccdf/1.2/xc<caret>cdf_1.2.xsd\" id=\"xccdf_N_benchmark_S\">\n" +
                              "</Benchmark>");
    IntentionAction intention = myFixture.getAvailableIntention(XmlBundle.message("xml.intention.fetch.name"));
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
    IntentionAction intention = myFixture.getAvailableIntention(XmlBundle.message("xml.intention.fetch.name"));
    assertNotNull(intention);
    intention.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
    List<HighlightInfo> infos = myFixture.doHighlighting();
    assertEmpty(infos);
    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManager.getInstance().removeResource(url));
  }
}
