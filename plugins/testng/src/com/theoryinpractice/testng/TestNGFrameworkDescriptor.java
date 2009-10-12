/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.theoryinpractice.testng;

import com.intellij.testIntegration.JavaTestFrameworkDescriptor;
import com.intellij.util.PathUtil;
import com.intellij.psi.PsiClass;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.Nullable;

public class TestNGFrameworkDescriptor extends JavaTestFrameworkDescriptor {
  public String getName() {
    return "TestNG";
  }

  protected String getMarkerClassFQName() {
    return "org.testng.annotations.Test";
  }

  public String getLibraryPath() {
    try {
      return PathUtil.getJarPathForClass(Class.forName("org.testng.annotations.Test"));
    }
    catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  @Nullable
  public String getDefaultSuperClass() {
    return null;
  }

  public boolean isTestClass(PsiClass clazz) {
    return TestNGUtil.isTestNGClass(clazz);
  }

  public FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("TestNG SetUp Method.java");
  }

  public FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("TestNG TearDown Method.java");
  }

  public FileTemplateDescriptor getTestMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("TestNG Test Method.java");
  }
}
