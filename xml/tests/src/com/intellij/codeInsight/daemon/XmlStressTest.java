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
import com.intellij.javaee.ExternalResourceManagerExImpl;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.xml.util.CheckDtdReferencesInspection;
import com.intellij.xml.util.CheckXmlFileWithXercesValidatorInspection;
import com.intellij.xml.util.XmlDuplicatedIdInspection;
import com.intellij.xml.util.XmlInvalidIdInspection;

import java.io.File;

public class XmlStressTest extends DaemonAnalyzerTestCase {

  public void testSchemaValidator() throws Exception {
    for (int i = 0; i < 100; i++) {
      doTest("xml/WsdlValidation.wsdl", false, false);
      System.out.println(i);
    }
  }

  private static final String BASE_PATH = "/xml/";

  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new RequiredAttributesInspection(),
      new XmlDuplicatedIdInspection(),
      new XmlInvalidIdInspection(),
      new CheckDtdReferencesInspection(),
      new XmlUnboundNsPrefixInspection(),
      new XmlPathReferenceInspection()
    };
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new CheckXmlFileWithXercesValidatorInspection());

    ExternalResourceManagerExImpl.registerResourceTemporarily("http://schemas.xmlsoap.org/wsdl/",
                                                              getTestDataPath() + BASE_PATH + "wsdl11.xsd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://schemas.xmlsoap.org/wsdl/soap/",
                                                              getTestDataPath() + BASE_PATH + "wsdl11_soapbinding.xsd",
                                                              getTestRootDisposable());
    ExternalResourceManagerExImpl.registerResourceTemporarily("http://schemas.xmlsoap.org/soap/encoding/",
                                                              getTestDataPath() + BASE_PATH + "soap-encoding.xsd",
                                                              getTestRootDisposable());
  }

  @Override
  protected String getTestDataPath() {
    return PlatformTestUtil.getCommunityPath().replace(File.separatorChar, '/') + "/xml/tests/testData/";
  }
}
