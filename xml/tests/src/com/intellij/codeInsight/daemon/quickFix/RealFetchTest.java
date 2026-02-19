// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnresolvedReferenceInspection;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.javaee.ExternalResourceManager;
import com.intellij.javaee.ExternalResourceManagerExImpl;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.testFramework.io.ExternalResourcesChecker;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.testFramework.JUnit38AssumeSupportRunner;
import com.intellij.testFramework.TestLoggerKt;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.ExceptionUtil;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlBundle;
import org.junit.Assume;
import org.junit.runner.RunWith;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
@RunWith(JUnit38AssumeSupportRunner.class)
public class RealFetchTest extends BasePlatformTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Assume.assumeFalse(IS_UNDER_SAFE_PUSH);
  }

  public void testFetchDtd() throws Exception {
    final String url = "http://java.sun.com/dtd/preferences.dtd";
    assertEquals(url, ExternalResourceManager.getInstance().getResourceLocation(url, getProject()));
    myFixture.configureByText(XmlFileType.INSTANCE, "<!DOCTYPE images SYSTEM \"http://java.sun.com/dtd/prefer<caret>ences.dtd\">");
    invokeFetchIntention(url);
    String location = ExternalResourceManager.getInstance().getResourceLocation(url, getProject());
    assertNotSame(url, location);
    assertTrue(location.endsWith("preferences.dtd")); // no ".xml" suffix added
    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManager.getInstance().removeResource(url));
  }

  private void invokeFetchIntention(String url) throws Exception {
    TestLoggerKt.rethrowLoggedErrorsIn(() -> {
      IntentionAction intention = myFixture.getAvailableIntention(XmlBundle.message("xml.intention.fetch.name"));
      assertNotNull(intention);
      try {
        intention.invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
      }
      catch (Throwable e) {
        Throwable cause = ExceptionUtil.getRootCause(e);
        if (cause.getMessage().startsWith(XmlBundle.message("xml.intention.fetch.error.fetching.title")) ||
            cause.getMessage().startsWith("Could not fetch")) {
          ExternalResourcesChecker.reportUnavailability(url, cause);
        }
        throw new RuntimeException(e);
      }
    });
  }

  public void testRelativePath() throws Exception {
    final String url = "https://community.rti.com/schema/6.0.0/rti_dds_qos_profiles.xsd";
    assertEquals(url, ExternalResourceManager.getInstance().getResourceLocation(url, getProject()));
    myFixture.configureByText(XmlFileType.INSTANCE,
                              """
                                <dds xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchemaLocation="https://community.rti.com/schema/6.0.0/rti_dds_qos_pr<caret>ofiles.xsd">
                                    <qos_library name="MyLibrary">
                                        <qos_profile name="MyProfile" base_name="BuiltinQosLib::Baseline.6.0.0">
                                        </qos_profile>
                                    </qos_library>
                                </dds>""");
    invokeFetchIntention(url);
    myFixture.testHighlighting();
    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManager.getInstance().removeResource(url));
  }

  public void testNestedRelativePath() throws Exception {
    final String url = "https://community.rti.com/schema/6.0.0/rti_dds_profiles.xsd";
    assertEquals(url, ExternalResourceManager.getInstance().getResourceLocation(url, getProject()));
    myFixture.configureByText(XmlFileType.INSTANCE,
                              """
                                <?xml version="1.0" encoding="UTF-8" ?>
                                <dds xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                     xsi:noNamespaceSchemaLocation="https://community.rti.com/schema/6.0.0/rti_dd<caret>s_profiles.xsd">
                                
                                  <types/>
                                  <domain_library name="xxx"/>
                                  <domain_participant_library name="ffff"/>
                                </dds>""");
    invokeFetchIntention(url);

    myFixture.testHighlighting();

    XmlFile file = (XmlFile)myFixture.getFile();
    XmlAttributeDescriptor descriptor = file.getRootTag().getSubTags()[1].getAttribute("name").getDescriptor();
    PsiFile containingFile = descriptor.getDeclaration().getContainingFile();
    assertNotNull(containingFile.getParent());
    assertEquals("definitions", containingFile.getParent().getName());

    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManager.getInstance().removeResource(url));
  }

  public void testAbsolutePath() throws Exception {
    String url = "https://csrc.nist.gov/schema/xccdf/1.2/xc<caret>cdf_1.2.xsd";
    myFixture.configureByText(XmlFileType.INSTANCE,
                              """
                                <Benchmark xmlns="http://checklists.nist.gov/xccdf/1.2"
                                           xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                                           xsi:schemaLocation="http://checklists.nist.gov/xccdf/1.2 https://csrc.nist.gov/schema/xccdf/1.2/xc<caret>cdf_1.2.xsd" id="xccdf_N_benchmark_S">
                                </Benchmark>""");
    invokeFetchIntention(url.replace("<caret>", ""));
    myFixture.testHighlighting();
    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManager.getInstance().removeResource(url));
  }

  public void testOverwriteFetchDtd() throws Exception {
    final String url = "http://java.sun.com/dtd/preferences.dtd";
    VirtualFile virtualFile = myFixture.getTempDirFixture().createFile("images.dtd", "");
    ExternalResourceManagerExImpl.registerResourceTemporarily(url, virtualFile.getPath(), getTestRootDisposable());

    myFixture.enableInspections(new XmlUnresolvedReferenceInspection());
    myFixture.configureByText(XmlFileType.INSTANCE, """
      <!DOCTYPE preferences SYSTEM "<error descr="Resource registered by this uri is not recognized (Settings | Languages & Frameworks | Schemas and DTDs)">http://java.sun.com/dtd/prefe<caret>rences.dtd</error>"><preferences>
        <root type="system"><map/></root>
      </preferences>""");
    myFixture.testHighlighting();
    invokeFetchIntention(url);
    List<HighlightInfo> infos = myFixture.doHighlighting();
    assertEmpty(infos);
    ApplicationManager.getApplication().runWriteAction(() -> ExternalResourceManager.getInstance().removeResource(url));
  }
}
