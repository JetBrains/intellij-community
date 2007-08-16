package com.theoryinpractice.testng.configuration;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ModuleBasedConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.psi.PsiDirectory;
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
    if (data.getScope() != TestSearchScope.WHOLE_PROJECT) {
      final Module predefinedModule = configuration.getConfigurationModule().getModule();
      if (predefinedModule == null) {
        Module module = null;
        if (element instanceof PsiDirectory) {
          module = VfsUtil.getModuleForFile(project, ((PsiDirectory)element).getVirtualFile());
        }
        if (module != null) {
          configuration.setModule(module);
        }
        else {
          data.setScope(TestSearchScope.WHOLE_PROJECT);
        }
      }
    } else {
      final RunnerAndConfigurationSettingsImpl template =
        ((RunManagerImpl)context.getRunManager()).getConfigurationTemplate(getConfigurationFactory());
      final Module selectedModule = (Module)context.getDataContext().getData(DataConstants.MODULE);
      if (selectedModule != null) {
        final ModuleBasedConfiguration templateConfiguration = (ModuleBasedConfiguration)template.getConfiguration();
        if (templateConfiguration.getConfigurationModule().getModule() == null) {
          configuration.setModule(selectedModule);
          data.setScope(TestSearchScope.SINGLE_MODULE);
        }
      }
    }
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