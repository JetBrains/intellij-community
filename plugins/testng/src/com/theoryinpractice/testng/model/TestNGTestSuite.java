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
package com.theoryinpractice.testng.model;

import com.intellij.execution.CantRunException;
import com.intellij.execution.configurations.RuntimeConfigurationException;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.psi.xml.XmlFile;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import org.testng.xml.Parser;

import java.util.List;
import java.util.Map;

public class TestNGTestSuite extends TestNGTestObject {
  private static final Object PARSE_LOCK = new Object();
  public TestNGTestSuite(TestNGConfiguration config) {
    super(config);
  }

  @Override
  public void fillTestObjects(Map<PsiClass, Map<PsiMethod, List<String>>> classes) throws CantRunException {}

  @Override
  public String getGeneratedName() {
    return myConfig.getPersistantData().getSuiteName();
  }

  @Override
  public String getActionName() {
    return myConfig.getPersistantData().getSuiteName();
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    final TestData data = myConfig.getPersistantData();
    try {
      final Parser parser = new Parser(data.getSuiteName());
      parser.setLoadClasses(false);
      synchronized (PARSE_LOCK) {
        parser.parse();//try to parse suite.xml
      }
    }
    catch (Exception e) {
      throw new RuntimeConfigurationException("Unable to parse '" + data.getSuiteName() + "' specified");
    }
  }

  @Override
  public boolean isConfiguredByElement(PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile instanceof XmlFile) {
      final VirtualFile virtualFile = PsiUtilCore.getVirtualFile(containingFile);
      return virtualFile != null && Comparing.strEqual(myConfig.getPersistantData().getSuiteName(), virtualFile.getPath());
    }
    return false;
  }
}
