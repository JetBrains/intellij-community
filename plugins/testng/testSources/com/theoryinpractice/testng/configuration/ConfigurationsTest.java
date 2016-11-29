/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
 * Date: 06-Jun-2007
 */
package com.theoryinpractice.testng.configuration;

import com.intellij.execution.Location;
import com.intellij.execution.PsiLocation;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.ConfigurationFromContext;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.RefactoringFactory;
import com.intellij.refactoring.RenameRefactoring;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.UIUtil;
import com.theoryinpractice.testng.model.TestType;
import org.jetbrains.annotations.NotNull;
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
    JavaTestFixtureFactory.getFixtureFactory();   // registers Java module fixture builder
    final IdeaTestFixtureFactory fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory();
    final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = fixtureFactory.createFixtureBuilder(getClass().getSimpleName());
    myFixture = fixtureFactory.createTempDirTestFixture();
    myFixture.setUp();

    FileUtil.copyDir(new File(PluginPathManager.getPluginHomePath("testng") + "/testData/runConfiguration/module1"),
                     new File(myFixture.getTempDirPath()), false);

    myProjectFixture = testFixtureBuilder.getFixture();
    final JavaModuleFixtureBuilder javaModuleFixtureBuilder = testFixtureBuilder.addModule(JavaModuleFixtureBuilder.class);
    javaModuleFixtureBuilder.addContentRoot(myFixture.getTempDirPath()).addSourceRoot("src");
    javaModuleFixtureBuilder.addLibrary("testng", PathUtil.getJarPathForClass(AfterMethod.class));
    myProjectFixture.setUp();

  }

  @AfterMethod
  public void tearDown() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          myProjectFixture.tearDown();
          myProjectFixture = null;
          myFixture.tearDown();
          myFixture = null;
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });
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
    new WriteCommandAction(project) {
      protected void run(@NotNull final Result result) throws Throwable {
        renameClass.run();
      }
    }.execute();
    Assert.assertEquals(newName, configuration.getPersistantData().getMainClassName());

    final PsiMethod notATestMethod = findNotATestMethod(psiClass);

    final RenameRefactoring renameNotATestMethod = RefactoringFactory.getInstance(project).createRename(notATestMethod, "aaaa");
    renameNotATestMethod.setSearchInComments(false);
    renameNotATestMethod.setSearchInNonJavaFiles(false);
    new WriteCommandAction(project) {
      protected void run(@NotNull final Result result) throws Throwable {
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
    configuration.setMethodConfiguration(new PsiLocation<>(project, method));
    final String newMethodName = "renamedTest";
    final RenameRefactoring renameMethod = RefactoringFactory.getInstance(project).createRename(method, newMethodName);
    renameMethod.setSearchInComments(false);
    renameMethod.setSearchInNonJavaFiles(false);
    new WriteCommandAction(project) {
      protected void run(@NotNull final Result result) throws Throwable {
        renameMethod.run();
      }
    }.execute();

    Assert.assertEquals(className, configuration.getPersistantData().getMainClassName());
    Assert.assertEquals(newMethodName, configuration.getPersistantData().getMethodName());

    final PsiMethod notATestMethod = findNotATestMethod(psiClass);
    final RenameRefactoring renameNotATestMethod1 = RefactoringFactory.getInstance(project).createRename(notATestMethod, "bbbbb");
    renameNotATestMethod1.setSearchInComments(false);
    renameNotATestMethod1.setSearchInNonJavaFiles(false);
    new WriteCommandAction(project) {
      protected void run(@NotNull final Result result) throws Throwable {
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
    PsiMethod testMethod = findTestMethod(psiClass);
    Assert.assertTrue(type.isConfigurationByLocation(configuration, new PsiLocation(project, psiClass)));
    Assert.assertFalse(type.isConfigurationByLocation(configuration, new PsiLocation(project, testMethod)));

    //method config
    configuration.setMethodConfiguration(new PsiLocation<>(project, testMethod));
    Assert.assertTrue(type.isConfigurationByLocation(configuration, new PsiLocation(project, testMethod)));
    Assert.assertFalse(type.isConfigurationByLocation(configuration, new PsiLocation(project, psiClass)));
  }

  @Test
  public void testCreateFromContext() {
    final Project project = myProjectFixture.getProject();
    final PsiClass psiClass = findTestClass(project);
    final TestNGInClassConfigurationProducer producer = new TestNGInClassConfigurationProducer();
    
    final MapDataContext dataContext = new MapDataContext();
    
    dataContext.put(CommonDataKeys.PROJECT, project);
    dataContext.put(LangDataKeys.MODULE, ModuleUtil.findModuleForPsiElement(psiClass));
    dataContext.put(Location.DATA_KEY, PsiLocation.fromPsiElement(psiClass));

    final ConfigurationFromContext fromContext = producer.createConfigurationFromContext(ConfigurationContext.getFromContext(dataContext));
    assert fromContext != null;
    final RunnerAndConfigurationSettings config = fromContext.getConfigurationSettings();
    final RunConfiguration runConfiguration = config.getConfiguration();
    Assert.assertTrue(runConfiguration instanceof TestNGConfiguration);

    TestNGConfigurationType t = (TestNGConfigurationType)runConfiguration.getType();
    Assert.assertTrue(t.isConfigurationByLocation(runConfiguration, new PsiLocation(project, psiClass)));
  }

  private static PsiClass findTestClass(final Project project) {
    final PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass("Testt", GlobalSearchScope.allScope(project));
    assert psiClass != null;
    return psiClass;
  }

  private static PsiMethod findNotATestMethod(final PsiClass psiClass) {
    final PsiMethod[] notATestMethods = psiClass.findMethodsByName("notATest", false);
    assert notATestMethods.length == 1;
    return notATestMethods[0];
  }

  private static TestNGConfiguration createConfiguration(final Project project) {
    final RunManagerEx manager = RunManagerEx.getInstanceEx(project);
    final RunnerAndConfigurationSettings settings =
      manager.createRunConfiguration("testt", TestNGConfigurationType.getInstance().getConfigurationFactories()[0]);
    manager.addConfiguration(settings, false);
    return (TestNGConfiguration)settings.getConfiguration();
  }
}
