/*
 * User: anna
 * Date: 23-May-2007
 */
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.ExecutionUtil;
import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.junit.RuntimeConfigurationProducer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.theoryinpractice.testng.util.TestNGUtil;
import org.jetbrains.annotations.Nullable;

public class TestNGConfigurationProducer extends RuntimeConfigurationProducer implements Cloneable {
  private PsiElement myPsiElement = null;

  public TestNGConfigurationProducer() {
    super(TestNGConfigurationType.getInstance());
  }

  public PsiElement getSourceElement() {
    return myPsiElement;
  }

  @Nullable
  protected RunnerAndConfigurationSettingsImpl createConfigurationByElement(Location location, ConfigurationContext context) {
    PsiElement element = location.getPsiElement();
    PsiClass psiClass;
    if (element instanceof PsiClass) {
      psiClass = (PsiClass)element;
    }
    else {
      psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    }
    if (psiClass == null) return null;
    if (!ExecutionUtil.isRunnableClass(psiClass)) return null;
    if (!TestNGUtil.hasTest(psiClass)) return null;
    myPsiElement = psiClass;
    final Project project = location.getProject();
    RunnerAndConfigurationSettings settings = cloneTemplateConfiguration(project, context);
    final TestNGConfiguration configuration = (TestNGConfiguration)settings.getConfiguration();
    final Module[] modules = configuration.getModules();
    final Module originalModule = modules == null || modules.length == 0 ? null : modules[0];
    configuration.setClassConfiguration(psiClass);
    PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class, false);
    if (method != null && TestNGUtil.hasTest(method)) {
      configuration.setMethodConfiguration(PsiLocation.fromPsiElement(project, method));
      myPsiElement = method;
    }
    configuration.restoreOriginalModule(originalModule);
    settings.setName(configuration.getName());
    return (RunnerAndConfigurationSettingsImpl)settings;
  }

  public int compareTo(Object o) {
    return PREFERED;
  }
}