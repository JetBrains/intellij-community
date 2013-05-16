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

/*
 * User: anna
 * Date: 23-May-2007
 */
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.JavaRunConfigurationExtensionManager;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.junit.InheritorChooser;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TestNGInClassConfigurationProducer extends TestNGConfigurationProducer{
  private PsiElement myPsiElement = null;

  public PsiElement getSourceElement() {
    return myPsiElement;
  }

  @Nullable
  protected RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context) {
    final PsiElement[] elements = context != null ? LangDataKeys.PSI_ELEMENT_ARRAY.getData(context.getDataContext()) : null;
    if (elements != null && TestNGPatternConfigurationProducer.collectTestMembers(elements).size() > 1) {
      return null;
    }

    PsiClass psiClass = null;
    PsiElement element = location.getPsiElement();
    while (element != null) {
      if (element instanceof PsiClass && isTestNGClass((PsiClass)element)) {
        psiClass = (PsiClass)element;
        break;
      }
      else if (element instanceof PsiMember) {
        psiClass = ((PsiMember)element).getContainingClass();
        if (isTestNGClass(psiClass)) {
          break;
        }
      }
      else if (element instanceof PsiClassOwner) {
        final PsiClass[] classes = ((PsiClassOwner)element).getClasses();
        if (classes.length == 1) {
          psiClass = classes[0];
          break;
        }
      }
      element = element.getParent();
    }
    if (!isTestNGClass(psiClass)) return null;

    myPsiElement = psiClass;
    final Project project = location.getProject();
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);
    final TestNGConfiguration configuration = (TestNGConfiguration)settings.getConfiguration();
    setupConfigurationModule(context, configuration);
    final Module originalModule = configuration.getConfigurationModule().getModule();
    configuration.setClassConfiguration(psiClass);

    PsiMethod method = PsiTreeUtil.getParentOfType(location.getPsiElement(), PsiMethod.class, false);
    while (method != null) {
      if (TestNGUtil.hasTest(method)) {
        configuration.setMethodConfiguration(PsiLocation.fromPsiElement(project, method));
        myPsiElement = method;
      }
      method = PsiTreeUtil.getParentOfType(method, PsiMethod.class);
    }

    configuration.restoreOriginalModule(originalModule);
    settings.setName(configuration.getName());
    JavaRunConfigurationExtensionManager.getInstance().extendCreatedConfiguration(configuration, location);
    return settings;
  }

  private static boolean isTestNGClass(PsiClass psiClass) {
    return psiClass != null && PsiClassUtil.isRunnableClass(psiClass, true, false) && TestNGUtil.hasTest(psiClass);
  }

  public int compareTo(Object o) {
    return PREFERED;
  }

  @Override
  public void perform(final ConfigurationContext context, final Runnable performRunnable) {
    if (myPsiElement instanceof PsiMethod || myPsiElement instanceof PsiClass) {

      final PsiMethod psiMethod;
      final PsiClass containingClass;

      if (myPsiElement instanceof PsiMethod) {
        psiMethod = (PsiMethod)myPsiElement;
        containingClass = psiMethod.getContainingClass();
      } else {
        psiMethod = null;
        containingClass = (PsiClass)myPsiElement;
      }

      final InheritorChooser inheritorChooser = new InheritorChooser() {
        @Override
        protected void runForClasses(List<PsiClass> classes, PsiMethod method, ConfigurationContext context, Runnable performRunnable) {
          ((TestNGConfiguration)context.getConfiguration().getConfiguration()).bePatternConfiguration(classes, method);
          super.runForClasses(classes, method, context, performRunnable);
        }

        @Override
        protected void runForClass(PsiClass aClass,
                                   PsiMethod psiMethod,
                                   ConfigurationContext context,
                                   Runnable performRunnable) {
          if (myPsiElement instanceof PsiMethod) {
            final Project project = psiMethod.getProject();
            final MethodLocation methodLocation = new MethodLocation(project, psiMethod, PsiLocation.fromPsiElement(aClass));
            ((TestNGConfiguration)context.getConfiguration().getConfiguration()).setMethodConfiguration(methodLocation);
          } else {
            ((TestNGConfiguration)context.getConfiguration().getConfiguration()).setClassConfiguration(aClass);
          }
          super.runForClass(aClass, psiMethod, context, performRunnable);
        }
      };
      if (inheritorChooser.runMethodInAbstractClass(context, performRunnable, psiMethod, containingClass)) return;
    }
    super.perform(context, performRunnable);
  }
}