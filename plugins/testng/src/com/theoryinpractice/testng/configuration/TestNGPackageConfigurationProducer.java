package com.theoryinpractice.testng.configuration;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestType;

public class TestNGPackageConfigurationProducer extends TestNGConfigurationProducer {
  private PsiPackage myPackage = null;

  protected RunnerAndConfigurationSettingsImpl createConfigurationByElement(final Location location, final ConfigurationContext context) {
    final Project project = location.getProject();
    final PsiElement element = location.getPsiElement();
    myPackage = checkPackage(element);
    if (myPackage == null) return null;
    RunnerAndConfigurationSettingsImpl settings = cloneTemplateConfiguration(project, context);
    final TestNGConfiguration configuration = (TestNGConfiguration)settings.getConfiguration();
    final TestData data = configuration.data;
    data.PACKAGE_NAME = myPackage.getQualifiedName();
    data.TEST_OBJECT = TestType.PACKAGE.getType();
    data.setScope(setupPackageConfiguration(context, project, configuration, data.getScope()));
    configuration.setGeneratedName();
    return settings;
  }

  public PsiElement getSourceElement() {
    return myPackage;
  }

  public int compareTo(final Object o) {
    return PREFERED;
  }
}