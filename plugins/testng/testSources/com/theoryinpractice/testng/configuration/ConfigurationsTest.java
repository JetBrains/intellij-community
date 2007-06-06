/*
 * User: anna
 * Date: 06-Jun-2007
 */
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
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

    FileUtil.copyDir(new File(PathManager.getHomePath() + "/plugins/testng/testData/runConfiguration/module1"), new File(myFixture.getTempDirPath()), false);
    
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
    final RenameRefactoring rename = RefactoringFactory.getInstance(project).createRename(psiClass, newName);
    rename.setSearchInComments(false);
    rename.setSearchInNonJavaFiles(false);
    new WriteCommandAction(project, null) {
      protected void run(final Result result) throws Throwable {
        rename.run();
      }
    }.execute();
    Assert.assertEquals(newName, configuration.getPersistantData().getMainClassName());
  }
}