// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.theoryinpractice.testng.inspection;

import com.intellij.codeInsight.daemon.impl.quickfix.CreateMethodQuickFix;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.ide.fileTemplates.FileTemplate;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.util.Version;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.theoryinpractice.testng.DataProviderReference;
import com.theoryinpractice.testng.TestNGFramework;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.DataProvider;

import java.util.Properties;

public class TestNGDataProviderInspection extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(final @NotNull ProblemsHolder holder, final boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitAnnotation(PsiAnnotation annotation) {
        if (TestNGUtil.TEST_ANNOTATION_FQN.equals(annotation.getQualifiedName())) {
          final PsiAnnotationMemberValue provider = annotation.findDeclaredAttributeValue("dataProvider");
          if (provider != null && !TestNGUtil.isDisabled(annotation)) {
            for (PsiReference reference : provider.getReferences()) {
              if (reference instanceof DataProviderReference) {
                final PsiElement dataProviderMethod = reference.resolve();
                final PsiElement element = reference.getElement();
                final PsiClass topLevelClass = PsiUtil.getTopLevelClass(element);
                final PsiClass providerClass = TestNGUtil.getProviderClass(element, topLevelClass);
                if (!(dataProviderMethod instanceof PsiMethod)) {
                  final LocalQuickFix[] fixes;
                  if (isOnTheFly && providerClass != null) {
                    fixes = new LocalQuickFix[] {createMethodFix(provider, providerClass, topLevelClass)};
                  }
                  else {
                    fixes = LocalQuickFix.EMPTY_ARRAY;
                  }

                  holder.registerProblem(provider, "Data provider does not exist", fixes);
                } else {
                  Version version = TestNGUtil.detectVersion(holder.getProject(), ModuleUtilCore.findModuleForPsiElement(providerClass));
                  if (version != null && version.isOrGreaterThan(6, 9, 13)) {
                    break;
                  }
                  final PsiMethod providerMethod = (PsiMethod)dataProviderMethod;
                  if (providerClass != topLevelClass && !providerMethod.hasModifierProperty(PsiModifier.STATIC)) {
                    holder.registerProblem(provider, "Data provider from foreign class need to be static");
                  }
                }
                break;
              }
            }
          }
        }
      }
    };
  }

  private static CreateMethodQuickFix createMethodFix(PsiAnnotationMemberValue provider,
                                                      @NotNull PsiClass providerClass,
                                                      PsiClass topLevelClass) {
    final String name = StringUtil.unquoteString(provider.getText());

    FileTemplateDescriptor templateDesc = new TestNGFramework().getParametersMethodFileTemplateDescriptor();
    assert templateDesc != null;
    final FileTemplate fileTemplate = FileTemplateManager.getInstance(provider.getProject()).getCodeTemplate(templateDesc.getFileName());

    String body = "";
    try {
      final Properties attributes = new Properties();
      attributes.put(FileTemplate.ATTRIBUTE_NAME, name);
      body = fileTemplate.getText(attributes);
      body = body.replace("${BODY}\n", "");
      final PsiMethod methodFromTemplate = JavaPsiFacade.getElementFactory(providerClass.getProject()).createMethodFromText(body, providerClass);
      final PsiCodeBlock methodBody = methodFromTemplate.getBody();
      if (methodBody != null) {
        body = StringUtil.trimEnd(StringUtil.trimStart(methodBody.getText(), "{"), "}");
      }
      else {
        body = "";
      }
    }
    catch (Exception ignored) {}
    if (StringUtil.isEmptyOrSpaces(body)) {
      body = "return new Object[][]{};";
    }

    String signature = "@" + DataProvider.class.getName() + " public ";
    if (providerClass == topLevelClass) {
      signature += "static ";
    }
    signature += "Object[][] " + name + "()";

    return CreateMethodQuickFix.createFix(providerClass, signature, body);
  }
}
