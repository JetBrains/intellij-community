/*
 * User: anna
 * Date: 06-Jun-2007
 */
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.PsiLocation;
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

    FileUtil.copyDir(new File(PathManager.getHomePath() + "/plugins/testng/testData/runConfiguration/module1"),
                     new File(myFixture.getTempDirPath()), false);

    myProjectFixture = testFixtureBuilder.getFixture();
    testFixtureBuilder.addModule(JavaModuleFixtureBuilder.class).addContentRoot(myFixture.getTempDirPath()).addSourceRoot("src");
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
  public void testRename() {
    final Project project = myProjectFixture.getProject();
    final PsiClass psiClass = PsiManager.getInstance(project).findClass("Testt");
    assert psiClass != null;
    final RunManagerEx manager = RunManagerEx.getInstanceEx(project);
    final RunnerAndConfigurationSettings settings =
      manager.createRunConfiguration("testt", TestNGConfigurationType.getInstance().getConfigurationFactories()[0]);
    manager.addConfiguration((RunnerAndConfigurationSettingsImpl)settings, false);
    final TestNGConfiguration configuration = (TestNGConfiguration)settings.getConfiguration();
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
    final PsiMethod[] psiMethods = psiClass.findMethodsByName("test", false);
    assert psiMethods.length == 1;
    final PsiMethod method = psiMethods[0];
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
    Assert.assertEquals(newName, configuration.getPersistantData().getMainClassName());
    Assert.assertEquals(newMethodName, configuration.getPersistantData().getMethodName());

    final PsiMethod[] notATestMethods = psiClass.findMethodsByName("notATest", false);
    assert notATestMethods.length == 1;
    final PsiMethod notATestMethod = notATestMethods[0];

    final RenameRefactoring renameNotATestMethod = RefactoringFactory.getInstance(project).createRename(notATestMethod, "aaaa");
    renameNotATestMethod.setSearchInComments(false);
    renameNotATestMethod.setSearchInNonJavaFiles(false);
    new WriteCommandAction(project, null) {
      protected void run(final Result result) throws Throwable {
        renameNotATestMethod.run();
      }
    }.execute();
    Assert.assertEquals(newName, configuration.getPersistantData().getMainClassName());
    Assert.assertEquals(newMethodName, configuration.getPersistantData().getMethodName());
  }
}