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

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.actions.RunConfigurationProducer;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.junit.InheritorChooser;
import com.intellij.execution.junit2.info.MethodLocation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.theoryinpractice.testng.util.TestNGUtil;

import java.util.List;

public abstract class AbstractTestNGInClassConfigurationProducer extends TestNGConfigurationProducer {

  protected AbstractTestNGInClassConfigurationProducer(ConfigurationType configurationType) {
    super(configurationType);
  }

  private static boolean isTestNGClass(PsiClass psiClass) {
    return psiClass != null && PsiClassUtil.isRunnableClass(psiClass, true, false) && TestNGUtil.hasTest(psiClass);
  }

  @Override
  public void onFirstRun(final ConfigurationFromContext configuration, final ConfigurationContext fromContext, Runnable performRunnable) {
    final PsiElement psiElement = configuration.getSourceElement();
    if (psiElement instanceof PsiMethod || psiElement instanceof PsiClass) {

      final PsiMethod psiMethod;
      final PsiClass containingClass;

      if (psiElement instanceof PsiMethod) {
        psiMethod = (PsiMethod)psiElement;
        containingClass = psiMethod.getContainingClass();
      } else {
        psiMethod = null;
        containingClass = (PsiClass)psiElement;
      }

      final InheritorChooser inheritorChooser = new InheritorChooser() {
        @Override
        protected void runForClasses(List<PsiClass> classes, PsiMethod method, ConfigurationContext context, Runnable performRunnable) {
          ((TestNGConfiguration)configuration.getConfiguration()).bePatternConfiguration(classes, method);
          super.runForClasses(classes, method, context, performRunnable);
        }

        @Override
        protected void runForClass(PsiClass aClass,
                                   PsiMethod psiMethod,
                                   ConfigurationContext context,
                                   Runnable performRunnable) {
          if (psiElement instanceof PsiMethod) {
            final Project project = psiMethod.getProject();
            final MethodLocation methodLocation = new MethodLocation(project, psiMethod, PsiLocation.fromPsiElement(aClass));
            ((TestNGConfiguration)configuration.getConfiguration()).setMethodConfiguration(methodLocation);
          } else {
            ((TestNGConfiguration)configuration.getConfiguration()).setClassConfiguration(aClass);
          }
          super.runForClass(aClass, psiMethod, context, performRunnable);
        }
      };
      if (inheritorChooser.runMethodInAbstractClass(fromContext, performRunnable, psiMethod, containingClass, new Condition<PsiClass>() {
        @Override
        public boolean value(PsiClass aClass) {
          return aClass.hasModifierProperty(PsiModifier.ABSTRACT) && TestNGUtil.hasTest(aClass);
        }
      })) return;
    }
    super.onFirstRun(configuration, fromContext, performRunnable);
  }

  @Override
  protected boolean setupConfigurationFromContext(TestNGConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    if (RunConfigurationProducer.getInstance(AbstractTestNGPatternConfigurationProducer.class).isMultipleElementsSelected(context)) {
      return false;
    }

    final Location contextLocation = context.getLocation();
    setupConfigurationParamName(configuration, contextLocation);

    PsiClass psiClass = null;
    PsiElement element = context.getPsiLocation();
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
    if (!isTestNGClass(psiClass)) return false;

    PsiElement psiElement = psiClass;
    final Project project = context.getProject();
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(context);
    setupConfigurationModule(context, configuration);
    final Module originalModule = configuration.getConfigurationModule().getModule();
    configuration.setClassConfiguration(psiClass);

    PsiMethod method = PsiTreeUtil.getParentOfType(context.getPsiLocation(), PsiMethod.class, false);
    while (method != null) {
      if (TestNGUtil.hasTest(method)) {
        configuration.setMethodConfiguration(PsiLocation.fromPsiElement(project, method));
        psiElement = method;
      }
      method = PsiTreeUtil.getParentOfType(method, PsiMethod.class);
    }

    configuration.restoreOriginalModule(originalModule);
    settings.setName(configuration.getName());
    sourceElement.set(psiElement);
    return true;
  }
}