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
import com.intellij.execution.RunConfigurationExtension;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Iterator;

public class TestNGInClassConfigurationProducer extends TestNGConfigurationProducer{
  private PsiElement myPsiElement = null;


  public PsiElement getSourceElement() {
    return myPsiElement;
  }

  @Nullable
  protected RunnerAndConfigurationSettings createConfigurationByElement(Location location, ConfigurationContext context) {
    PsiElement element = location.getPsiElement();
    PsiClass psiClass = null;
    for (Iterator<Location<PsiClass>> iterator = location.getAncestors(PsiClass.class, false); iterator.hasNext();) {
      psiClass = iterator.next().getPsiElement();
      if (TestNGUtil.hasTest(psiClass)) break;
    }
    if (psiClass == null) {
      if (element instanceof PsiClassOwner) {
        PsiClass[] classes = ((PsiClassOwner)element).getClasses();
        if (classes.length == 1) psiClass = classes[0];
      }
    }
    if (psiClass == null) return null;
    if (!PsiClassUtil.isRunnableClass(psiClass, true)) return null;
    if (!TestNGUtil.hasTest(psiClass)) return null;
    myPsiElement = psiClass;
    final Project project = location.getProject();
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);
    final TestNGConfiguration configuration = (TestNGConfiguration)settings.getConfiguration();
    setupConfigurationModule(context, configuration);
    final Module originalModule = configuration.getConfigurationModule().getModule();
    configuration.setClassConfiguration(psiClass);
    PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    if (method != null && TestNGUtil.hasTest(method)) {
      configuration.setMethodConfiguration(PsiLocation.fromPsiElement(project, method));
      myPsiElement = method;
    }
    configuration.restoreOriginalModule(originalModule);
    settings.setName(configuration.getName());
    RunConfigurationExtension.patchCreatedConfiguration(configuration);
    return settings;
  }

  public int compareTo(Object o) {
    return PREFERED;
  }
}