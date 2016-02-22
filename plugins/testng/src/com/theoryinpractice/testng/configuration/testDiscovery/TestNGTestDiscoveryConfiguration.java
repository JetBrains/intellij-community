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
package com.theoryinpractice.testng.configuration.testDiscovery;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.testDiscovery.TestDiscoveryConfiguration;
import com.intellij.execution.testDiscovery.TestDiscoverySearchHelper;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.theoryinpractice.testng.configuration.SearchingForTestsTask;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.configuration.TestNGConfigurationType;
import com.theoryinpractice.testng.configuration.TestNGRunnableState;
import com.theoryinpractice.testng.model.TestNGTestPattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class TestNGTestDiscoveryConfiguration extends TestDiscoveryConfiguration {

  public TestNGTestDiscoveryConfiguration(String name, Project project, ConfigurationFactory factory) {
    super(name, new JavaRunConfigurationModule(project, false), factory,
          new TestNGConfiguration("", project, TestNGConfigurationType.getInstance().getConfigurationFactories()[0]));
  }

  @Override
  protected ModuleBasedConfiguration createInstance() {
    final TestNGTestDiscoveryConfigurationType configurationType =
      ConfigurationTypeUtil.findConfigurationType(TestNGTestDiscoveryConfigurationType.class);
    final ConfigurationFactory[] factories = configurationType.getConfigurationFactories();
    return new TestNGTestDiscoveryConfiguration(getName(), getProject(), factories[0]);
  }

  @Nullable
  @Override
  public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment environment) throws ExecutionException {
    return new TestNGTestDiscoveryRunnableState(environment);
  }

  @Nullable
  @Override
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    return null;
  }

  @NotNull
  @Override
  public String getFrameworkPrefix() {
    return "g";
  }

  private class TestNGTestDiscoveryRunnableState extends TestNGRunnableState {
    public TestNGTestDiscoveryRunnableState(ExecutionEnvironment environment) {
      super(environment, ((TestNGConfiguration)myDelegate));
    }

    @Override
    protected TestSearchScope getScope() {
      return TestSearchScope.MODULE_WITH_DEPENDENCIES;
    }

    @Override
    protected boolean forkPerModule() {
      return spansMultipleModules("");
    }

    @Override
    public SearchingForTestsTask createSearchingForTestsTask() {
      return new SearchingForTestsTask(myServerSocket, getConfiguration(), myTempFile, client) {
        @Override
        protected void search() throws CantRunException {
          myClasses.clear();
          final Set<String> patterns = TestDiscoverySearchHelper.search(getProject(), getPosition(), getChangeList(), getFrameworkPrefix());
          final Module module = getConfigurationModule().getModule();
          final GlobalSearchScope searchScope =
            module != null ? GlobalSearchScope.moduleWithDependenciesScope(module) : GlobalSearchScope.projectScope(getProject());
          TestNGTestPattern.fillTestObjects(myClasses, patterns, TestSearchScope.MODULE_WITH_DEPENDENCIES, 
                                            TestNGTestDiscoveryConfiguration.this, searchScope);
        }

        @Override
        protected void onFound() {
          super.onFound();
          writeClassesPerModule(myClasses);
        }
      };
    }
  }
}
