/*
 * User: anna
 * Date: 06-Jun-2007
 */
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.PathUtil;
import com.theoryinpractice.testng.model.TestType;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.io.File;

public class ConfigurationsTest {
  private TempDirTestFixture myFixture;
  private IdeaProjectTestFixture myProjectFixture;


  @BeforeMethod
  public void setUp() throws Exception {
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = fixtureFactory.createFixtureBuilder();
    myFixture = fixtureFactory.createTempDirTestFixture();
    myFixture.setUp();

    FileUtil.copyDir(new File(PathManager.getHomePath() + "/svnPlugins/testng/testData/runConfiguration/module1"),
                     new File(myFixture.getTempDirPath()), false);

    myProjectFixture = testFixtureBuilder.getFixture();
    final JavaModuleFixtureBuilder javaModuleFixtureBuilder = testFixtureBuilder.addModule(JavaModuleFixtureBuilder.class);
    javaModuleFixtureBuilder.addContentRoot(myFixture.getTempDirPath()).addSourceRoot("src");
    javaModuleFixtureBuilder.addLibrary("testng", PathUtil.getJarPathForClass(AfterMethod.class));
    myProjectFixture.setUp();
    ((RunManagerImpl)RunManagerEx.getInstanceEx(myProjectFixture.getProject())).installRefactoringListener();
  }

  @AfterMethod
  public void tearDown() throws Exception {
    ((RunManagerImpl)RunManagerEx.getInstanceEx(myProjectFixture.getProject())).uninstallRefactoringListener();
    myProjectFixture.tearDown();
    myProjectFixture = null;
    myFixture.tearDown();
    myFixture = null;
  }

  @Test
  public void testClassRename() {
    final Project project = myProjectFixture.getProject();
    final PsiClass psiClass = findTestClass(project);
    final TestNGConfiguration configuration = createConfiguration(project);
    configuration.setClassConfiguration(psiClass);
    final String newName = "Testt1";
    final RenameRefactoring renameClass = RefactoringFactory.getInstance(project).createRename(psiClass, newName);
    renameClass.setSearchInComments(false);
    renameClass.setSearchInNonJavaFiles(false);
    new WriteCommandAction(project, null) {
      protected void run(final Result result) throws Throwable {
        renameClass.run();
      }
    }.execute();
    Assert.assertEquals(newName, configuration.getPersistantData().getMainClassName());

    final PsiMethod notATestMethod = findNotATestMethod(psiClass);

    final RenameRefactoring renameNotATestMethod = RefactoringFactory.getInstance(project).createRename(notATestMethod, "aaaa");
    renameNotATestMethod.setSearchInComments(false);
    renameNotATestMethod.setSearchInNonJavaFiles(false);
    new WriteCommandAction(project, null) {
      protected void run(final Result result) throws Throwable {
        renameNotATestMethod.run();
      }
    }.execute();
    Assert.assertEquals(configuration.getPersistantData().getMainClassName(), newName);
    Assert.assertEquals(configuration.getPersistantData().getMethodName(), "");
    Assert.assertEquals(configuration.getPersistantData().TEST_OBJECT, TestType.CLASS.getType());
  }


  @Test
  public void testRenameMethod() {
    final Project project = myProjectFixture.getProject();
    final PsiClass psiClass = findTestClass(project);
    final String className = psiClass.getName();
    final TestNGConfiguration configuration = createConfiguration(project);

    final PsiMethod method = findTestMethod(psiClass);
    configuration.setMethodConfiguration(new PsiLocation<PsiMethod>(project, method));
    final String newMethodName = "renamedTest";
    final RenameRefactoring renameMethod = RefactoringFactory.getInstance(project).createRename(method, newMethodName);
    renameMethod.setSearchInComments(false);
    renameMethod.setSearchInNonJavaFiles(false);
    new WriteCommandAction(project, null) {
      protected void run(final Result result) throws Throwable {
        renameMethod.run();
      }
    }.execute();

    Assert.assertEquals(className, configuration.getPersistantData().getMainClassName());
    Assert.assertEquals(newMethodName, configuration.getPersistantData().getMethodName());

    final PsiMethod notATestMethod = findNotATestMethod(psiClass);
    final RenameRefactoring renameNotATestMethod1 = RefactoringFactory.getInstance(project).createRename(notATestMethod, "bbbbb");
    renameNotATestMethod1.setSearchInComments(false);
    renameNotATestMethod1.setSearchInNonJavaFiles(false);
    new WriteCommandAction(project, null) {
      protected void run(final Result result) throws Throwable {
        renameNotATestMethod1.run();
      }
    }.execute();
    Assert.assertEquals(className, configuration.getPersistantData().getMainClassName());
    Assert.assertEquals(newMethodName, configuration.getPersistantData().getMethodName());
  }

  private PsiMethod findTestMethod(final PsiClass psiClass) {
    final PsiMethod[] psiMethods = psiClass.findMethodsByName("test", false);
    assert psiMethods.length == 1;
    return psiMethods[0];
  }

  @Test
  public void testReuseOrCreateNewConfiguration() {
    final Project project = myProjectFixture.getProject();
    final PsiClass psiClass = findTestClass(project);
    final TestNGConfiguration configuration = createConfiguration(project);
    final TestNGConfigurationType type = (TestNGConfigurationType)configuration.getFactory().getType();

    //class config
    configuration.setClassConfiguration(psiClass);
    Assert.assertTrue(type.isConfigurationByElement(configuration, project, psiClass));
    final PsiMethod testMethod = findTestMethod(psiClass);
    Assert.assertFalse(type.isConfigurationByElement(configuration, project, testMethod));

    //method config
    configuration.setMethodConfiguration(new PsiLocation<PsiMethod>(project, testMethod));
    Assert.assertTrue(type.isConfigurationByElement(configuration, project, testMethod));
    Assert.assertFalse(type.isConfigurationByElement(configuration, project, psiClass));
  }

  @Test
  public void testCreateFromContext() {
    final Project project = myProjectFixture.getProject();
    final PsiClass psiClass = findTestClass(project);
    final TestNGInClassConfigurationProducer producer = new TestNGInClassConfigurationProducer();
    final RunnerAndConfigurationSettingsImpl config = producer.createConfigurationByElement(new PsiLocation<PsiClass>(project, psiClass), null);
    assert config != null;
    final RunConfiguration runConfiguration = config.getConfiguration();
    Assert.assertTrue(runConfiguration instanceof TestNGConfiguration);
    Assert.assertTrue(((TestNGConfigurationType)runConfiguration.getType()).isConfigurationByElement(runConfiguration, project, psiClass));
  }

  private PsiClass findTestClass(final Project project) {
    final PsiClass psiClass = PsiManager.getInstance(project).findClass("Testt");
    assert psiClass != null;
    return psiClass;
  }

  private PsiMethod findNotATestMethod(final PsiClass psiClass) {
    final PsiMethod[] notATestMethods = psiClass.findMethodsByName("notATest", false);
    assert notATestMethods.length == 1;
    return notATestMethods[0];
  }

  private TestNGConfiguration createConfiguration(final Project project) {
    final RunManagerEx manager = RunManagerEx.getInstanceEx(project);
    final RunnerAndConfigurationSettings settings =
      manager.createRunConfiguration("testt", TestNGConfigurationType.getInstance().getConfigurationFactories()[0]);
    manager.addConfiguration((RunnerAndConfigurationSettingsImpl)settings, false);
    return (TestNGConfiguration)settings.getConfiguration();
  }
}