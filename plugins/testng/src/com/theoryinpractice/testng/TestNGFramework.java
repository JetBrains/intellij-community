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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.testIntegration.JavaTestFramework;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PathUtil;
import com.theoryinpractice.testng.configuration.TestNGConfigurationType;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;

import javax.swing.*;

public class TestNGFramework extends JavaTestFramework {
  @NotNull
  public String getName() {
    return "TestNG";
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return TestNGConfigurationType.ICON;
  }

  protected String getMarkerClassFQName() {
    return "org.testng.annotations.Test";
  }

  @NotNull
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

  public boolean isTestClass(PsiClass clazz, boolean canBePotential) {
    if (canBePotential) return isUnderTestSources(clazz);
    return TestNGUtil.isTestNGClass(clazz);
  }

  @Nullable
  @Override
  protected PsiMethod findSetUpMethod(@NotNull PsiClass clazz) {
    for (PsiMethod each : clazz.getMethods()) {
      if (AnnotationUtil.isAnnotated(each, "org.testng.annotations.BeforeMethod", false)) return each;
    }
    return null;
  }

  @Nullable
  @Override
  protected PsiMethod findTearDownMethod(@NotNull PsiClass clazz) {
    for (PsiMethod each : clazz.getMethods()) {
      if (AnnotationUtil.isAnnotated(each, "org.testng.annotations.AfterMethod", false)) return each;
    }
    return null;
  }

  @Override
  protected PsiMethod findOrCreateSetUpMethod(PsiClass clazz) throws IncorrectOperationException {
    final PsiManager manager = clazz.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    PsiMethod patternMethod =
      factory.createMethodFromText("@org.testng.annotations.BeforeMethod\n protected void setUp() throws Exception {}", null);

    final PsiClass superClass = clazz.getSuperClass();
    if (superClass != null) {
      final PsiMethod[] methods = superClass.findMethodsBySignature(patternMethod, false);
      if (methods.length > 0) {
        final PsiModifierList modifierList = methods[0].getModifierList();
        if (!modifierList.hasModifierProperty(PsiModifier.PRIVATE)) { //do not override private method
          @NonNls String pattern = "@org.testng.annotations.BeforeMethod\n";
          if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
            pattern += "protected ";
          }
          else if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
            pattern += "public ";
          }
          patternMethod = factory.createMethodFromText(pattern + "void setUp() throws Exception {\nsuper.setUp();\n}", null);
        }
      }
    }

    final PsiMethod[] psiMethods = clazz.getMethods();
    PsiMethod inClass = null;
    for (PsiMethod psiMethod : psiMethods) {
      if (AnnotationUtil.isAnnotated(psiMethod, BeforeMethod.class.getName(), false)) {
        inClass = psiMethod;
        break;
      }
    }
    if (inClass == null) {
      final PsiMethod psiMethod = (PsiMethod)clazz.add(patternMethod);
      JavaCodeStyleManager.getInstance(clazz.getProject()).shortenClassReferences(clazz);
      return psiMethod;
    }
    else if (inClass.getBody() == null) {
      return (PsiMethod)inClass.replace(patternMethod);
    }
    return inClass;
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
