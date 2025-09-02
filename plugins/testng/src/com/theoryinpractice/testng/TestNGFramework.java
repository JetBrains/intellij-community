// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationPsiFix;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.roots.ExternalLibraryDescriptor;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.testIntegration.JavaTestFramework;
import com.intellij.util.IncorrectOperationException;
import com.theoryinpractice.testng.configuration.TestNGConfigurationType;
import com.theoryinpractice.testng.intention.TestNGExternalLibraryResolver;
import com.theoryinpractice.testng.util.TestNGUtil;
import icons.TestngIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import javax.swing.*;
import java.util.Arrays;
import java.util.List;

public class TestNGFramework extends JavaTestFramework implements DumbAware {
  private static final List<String> SECONDARY_BEFORE_ANNOTATIONS = Arrays.asList("org.testng.annotations.BeforeTest",
                                                                                 "org.testng.annotations.BeforeClass",
                                                                                 "org.testng.annotations.BeforeSuite",
                                                                                 "org.testng.annotations.BeforeGroups"
  );

  @Override
  public @NotNull String getName() {
    return "TestNG";
  }

  @Override
  public @NotNull Icon getIcon() {
    return TestngIcons.TestNG;
  }

  @Override
  protected String getMarkerClassFQName() {
    return "org.testng.annotations.Test";
  }

  @Override
  public ExternalLibraryDescriptor getFrameworkLibraryDescriptor() {
    return TestNGExternalLibraryResolver.TESTNG_DESCRIPTOR;
  }

  @Override
  public @Nullable String getDefaultSuperClass() {
    return null;
  }

  @Override
  public boolean isTestClass(PsiClass clazz, boolean canBePotential) {
    if (clazz == null) return false;
    return callWithAlternateResolver(clazz.getProject(), () -> {
      if (canBePotential) return isUnderTestSources(clazz);
      return TestNGUtil.isTestNGClass(clazz);
    }, false);
  }

  @Override
  protected @Nullable PsiMethod findSetUpMethod(@NotNull PsiClass clazz) {
    return callWithAlternateResolver(clazz.getProject(), () -> {
      for (PsiMethod each : clazz.getMethods()) {
        if (AnnotationUtil.isAnnotated(each, "org.testng.annotations.BeforeMethod", 0)) return each;
      }
      return null;
    }, null);
  }

  @Override
  protected @Nullable PsiMethod findBeforeClassMethod(@NotNull PsiClass clazz) {
    return callWithAlternateResolver(clazz.getProject(), () -> {
      for (PsiMethod each : clazz.getMethods()) {
        if (AnnotationUtil.isAnnotated(each, "org.testng.annotations.BeforeClass", 0)) return each;
      }
      return null;
    }, null);
  }

  @Override
  protected @Nullable PsiMethod findTearDownMethod(@NotNull PsiClass clazz) {
    return callWithAlternateResolver(clazz.getProject(), () -> {
      for (PsiMethod each : clazz.getMethods()) {
        if (AnnotationUtil.isAnnotated(each, "org.testng.annotations.AfterMethod", 0)) return each;
      }
      return null;
    }, null);
  }

  @Override
  protected @Nullable PsiMethod findAfterClassMethod(@NotNull PsiClass clazz) {
    return callWithAlternateResolver(clazz.getProject(), () -> {
      for (PsiMethod each : clazz.getMethods()) {
        if (AnnotationUtil.isAnnotated(each, "org.testng.annotations.AfterClass", 0)) return each;
      }
      return null;
    }, null);
  }


  @Override
  protected PsiMethod findOrCreateSetUpMethod(PsiClass clazz) throws IncorrectOperationException {
    PsiMethod method = findSetUpMethod(clazz);
    if (method != null) return method;

    final PsiManager manager = clazz.getManager();
    final PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    String setUpName = "setUp";
    PsiMethod patternMethod = createSetUpPatternMethod(factory);
    PsiMethod inClass = clazz.findMethodBySignature(patternMethod, false);
    if (inClass != null) {
      if (AnnotationUtil.isAnnotated(inClass, SECONDARY_BEFORE_ANNOTATIONS, 0)) {
        return inClass;
      }
      int exit = ApplicationManager.getApplication().isUnitTestMode() ?
                 Messages.YES :
                 Messages.showYesNoDialog(manager.getProject(), TestngBundle.message("testng.create.setup.dialog.message", setUpName),
                                          TestngBundle.message("testng.create.setup.dialog.title"),
                                          TestngBundle.message("testng.annotate.dialog.title"),
                                          TestngBundle.message("testng.create.new.method.dialog.title"),
                                          Messages.getWarningIcon());
      if (exit == Messages.YES) {
        AddAnnotationPsiFix.addPhysicalAnnotationIfAbsent(BeforeMethod.class.getName(), PsiNameValuePair.EMPTY_ARRAY, inClass.getModifierList());
        return inClass;
      }
      else if (exit == Messages.NO) {
        inClass = null;
        int i = 0;
        while (clazz.findMethodBySignature(patternMethod, false) != null) {
          patternMethod.setName(setUpName + (++i));
        }
        setUpName = patternMethod.getName();
      }
    }

    final PsiClass superClass = clazz.getSuperClass();
    if (superClass != null) {
      final PsiMethod[] methods = superClass.findMethodsBySignature(patternMethod, false);
      if (methods.length > 0) {
        final PsiModifierList modifierList = methods[0].getModifierList();
        if (!modifierList.hasModifierProperty(PsiModifier.PRIVATE)) { //do not override private method
          @NonNls String pattern = "@" + BeforeMethod.class.getName() + "\n";
          if (modifierList.hasModifierProperty(PsiModifier.PROTECTED)) {
            pattern += "protected ";
          }
          else if (modifierList.hasModifierProperty(PsiModifier.PUBLIC)) {
            pattern += "public ";
          }
          patternMethod =
            factory.createMethodFromText(pattern + "void " + setUpName + "() throws Exception {\nsuper." + setUpName + "();\n}", null);
        }
      }
    }

    final PsiMethod[] psiMethods = clazz.getMethods();

    PsiMethod testMethod = null;
    for (PsiMethod psiMethod : psiMethods) {
      if (inClass == null && AnnotationUtil.isAnnotated(psiMethod, BeforeMethod.class.getName(), 0)) {
        inClass = psiMethod;
      }
      if (testMethod == null &&
          AnnotationUtil.isAnnotated(psiMethod, Test.class.getName(), 0) &&
          !psiMethod.hasModifierProperty(PsiModifier.PRIVATE)) {
        testMethod = psiMethod;
      }
    }
    if (inClass == null) {
      final PsiMethod psiMethod;
      if (testMethod != null) {
        psiMethod = (PsiMethod)clazz.addBefore(patternMethod, testMethod);
      }
      else {
        psiMethod = (PsiMethod)clazz.add(patternMethod);
      }
      JavaCodeStyleManager.getInstance(clazz.getProject()).shortenClassReferences(clazz);
      return psiMethod;
    }
    else if (inClass.getBody() == null) {
      return (PsiMethod)inClass.replace(patternMethod);
    }
    return inClass;
  }

  @Override
  public FileTemplateDescriptor getTestClassFileTemplateDescriptor() {
    return new FileTemplateDescriptor("TestNG Test Class.java");
  }

  @Override
  public FileTemplateDescriptor getSetUpMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("TestNG SetUp Method.java");
  }

  @Override
  public FileTemplateDescriptor getBeforeClassMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("TestNG BeforeClass Method.java");
  }

  @Override
  public FileTemplateDescriptor getTearDownMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("TestNG TearDown Method.java");
  }

  @Override
  public FileTemplateDescriptor getAfterClassMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("TestNG AfterClass Method.java");
  }

  @Override
  public @NotNull FileTemplateDescriptor getTestMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("TestNG Test Method.java");
  }

  @Override
  public @Nullable FileTemplateDescriptor getParametersMethodFileTemplateDescriptor() {
    return new FileTemplateDescriptor("TestNG Parameters Method.java");
  }

  @Override
  public boolean isTestMethod(PsiElement element, boolean checkAbstract) {
    if (element == null) return false;
    return callWithAlternateResolver(element.getProject(), () -> {
      return element instanceof PsiMethod && isFrameworkAvailable(element) && TestNGUtil.hasTest((PsiModifierListOwner)element);
    }, false);
  }

  @Override
  public boolean isMyConfigurationType(ConfigurationType type) {
    return type instanceof TestNGConfigurationType;
  }
}
