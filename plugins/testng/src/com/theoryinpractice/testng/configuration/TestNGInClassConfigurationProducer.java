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
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.Nullable;

public class TestNGInClassConfigurationProducer extends TestNGConfigurationProducer{
  private PsiElement myPsiElement = null;

  public PsiElement getSourceElement() {
    return myPsiElement;
  }

  @Nullable
  protected RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context) {
    PsiClass psiClass = null;
    PsiElement element = location.getPsiElement();
    while (element != null) {
      if (element instanceof PsiClass) {
        psiClass = (PsiClass)element;
        break;
      }
      else if (element instanceof PsiMember) {
        psiClass = ((PsiMember)element).getContainingClass();
        break;
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
    if (psiClass == null || !PsiClassUtil.isRunnableClass(psiClass, true) || !TestNGUtil.hasTest(psiClass)) return null;

    myPsiElement = psiClass;
    final Project project = location.getProject();
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);
    final TestNGConfiguration configuration = (TestNGConfiguration)settings.getConfiguration();
    setupConfigurationModule(context, configuration);
    final Module originalModule = configuration.getConfigurationModule().getModule();
    configuration.setClassConfiguration(psiClass);

    final PsiMethod method = PsiTreeUtil.getParentOfType(location.getPsiElement(), PsiMethod.class, false);
    if (method != null && TestNGUtil.hasTest(method)) {
      configuration.setMethodConfiguration(PsiLocation.fromPsiElement(project, method));
      myPsiElement = method;
    }

    configuration.restoreOriginalModule(originalModule);
    settings.setName(configuration.getName());
    copyStepsBeforeRun(project, configuration);
    RunConfigurationExtension.patchCreatedConfiguration(configuration);
    return (RunnerAndConfigurationSettingsImpl)settings;
  }

  public int compareTo(Object o) {
    return PREFERED;
  }
}
