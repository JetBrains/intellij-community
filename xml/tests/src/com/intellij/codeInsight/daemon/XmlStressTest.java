/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.analysis.XmlPathReferenceInspection;
import com.intellij.codeInsight.daemon.impl.analysis.XmlUnboundNsPrefixInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.htmlInspections.RequiredAttributesInspection;
import com.intellij.javaee.ExternalResourceManagerExBase;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.junit5.StressTestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.xml.util.CheckDtdReferencesInspection;
import com.intellij.xml.util.CheckXmlFileWithXercesValidatorInspection;
import com.intellij.xml.util.XmlDuplicatedIdInspection;
import com.intellij.xml.util.XmlInvalidIdInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static com.intellij.codeInsight.daemon.DaemonAnalyzerFixture.daemonAnalyzerFixture;

@StressTestApplication
public class XmlStressTest {
  private static final Logger LOG = Logger.getInstance(XmlStressTest.class);
  private static final String TEST_DATA_PATH = PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";

  private final TestFixture<DaemonAnalyzerFixture> daemonFixture = daemonAnalyzerFixture(TEST_DATA_PATH, () -> new LocalInspectionTool[]{
    new RequiredAttributesInspection(),
    new XmlDuplicatedIdInspection(),
    new XmlInvalidIdInspection(),
    new CheckDtdReferencesInspection(),
    new XmlUnboundNsPrefixInspection(),
    new XmlPathReferenceInspection()
  });

  @Test
  public void testSchemaValidator() throws Exception {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    for (int i = 0; i < 100; i++) {
      fixture.doTest("xml/WsdlValidation.wsdl", false, false);
      LOG.debug(String.valueOf(i));
    }
  }

  private static final String BASE_PATH = "/xml/";

  @BeforeEach
  void setUp() {
    DaemonAnalyzerFixture fixture = daemonFixture.get();
    fixture.enableInspectionTool(new CheckXmlFileWithXercesValidatorInspection());

    ExternalResourceManagerExBase.registerResourceTemporarily("http://schemas.xmlsoap.org/wsdl/",
                                                              TEST_DATA_PATH + BASE_PATH + "wsdl11.xsd",
                                                              fixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://schemas.xmlsoap.org/wsdl/soap/",
                                                              TEST_DATA_PATH + BASE_PATH + "wsdl11_soapbinding.xsd",
                                                              fixture.getTestRootDisposable());
    ExternalResourceManagerExBase.registerResourceTemporarily("http://schemas.xmlsoap.org/soap/encoding/",
                                                              TEST_DATA_PATH + BASE_PATH + "soap-encoding.xsd",
                                                              fixture.getTestRootDisposable());
  }
}
