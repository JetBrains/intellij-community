// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.theoryinpractice.testng.configuration.testDiscovery;

import com.intellij.execution.CantRunException;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.target.TargetEnvironment;
import com.intellij.execution.testDiscovery.TestDiscoverySearchHelper;
import com.intellij.execution.testframework.SearchForTestsTask;
import com.intellij.execution.testframework.TestSearchScope;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.search.GlobalSearchScope;
import com.theoryinpractice.testng.configuration.SearchingForTestsTask;
import com.theoryinpractice.testng.configuration.TestNGConfiguration;
import com.theoryinpractice.testng.configuration.TestNGRunnableState;
import com.theoryinpractice.testng.model.TestData;
import com.theoryinpractice.testng.model.TestNGTestPattern;
import com.theoryinpractice.testng.model.TestType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class TestNGTestDiscoveryRunnableState extends TestNGRunnableState {

  private static final Logger LOG = Logger.getInstance(TestNGTestDiscoveryRunnableState.class);


  public TestNGTestDiscoveryRunnableState(ExecutionEnvironment environment,
                                          TestNGConfiguration configuration) {
    super(environment, configuration);
  }

  @Override
  protected TestSearchScope getScope() {
    return TestSearchScope.MODULE_WITH_DEPENDENCIES;
  }

  @Override
  protected boolean forkPerModule() {
    return getConfiguration().getConfigurationModule().getModule() == null;
  }

  @Override
  public @Nullable SearchForTestsTask createSearchingForTestsTask(@NotNull TargetEnvironment targetEnvironment) {
    return new SearchingForTestsTask(getServerSocket(), getConfiguration(), myTempFile) {
      @Override
      protected void search() throws CantRunException {
        Project project = getProject();
        if (project == null) return;
        try {
          CantRunException exception = DumbService.getInstance(project).computeWithAlternativeResolveEnabled(() -> {
            myClasses.clear();
            final TestData data = getConfiguration().getPersistantData();
            final Pair<String, String> position = data.TEST_OBJECT.equals(TestType.SOURCE.getType())
                                                  ? Pair.create(data.getMainClassName(), data.getMethodName()) : null;
            final Set<String> patterns = TestDiscoverySearchHelper
              .search(project, position, data.getChangeList(), getConfiguration().getTestFrameworkId());
            final Module module = getConfiguration().getConfigurationModule().getModule();
            final GlobalSearchScope searchScope =
              module != null ? GlobalSearchScope.moduleWithDependenciesScope(module) : GlobalSearchScope.projectScope(project);
            try {
              TestNGTestPattern.fillTestObjects(myClasses, patterns, TestSearchScope.MODULE_WITH_DEPENDENCIES,
                                                getConfiguration(), searchScope);
            }
            catch (CantRunException e) {
              return e;
            }
            return null;
          });
          if (exception != null) {
            throw exception;
          }
        }
        catch (IndexNotReadyException e) {
          LOG.error(e);
        }
      }

      /** Writes the fork file first, because it reads PSI. After super.onFound(), a restart would add the suite twice. */
      @Override
      protected void onFound() {
        writeClassesPerModule(myClasses);
        super.onFound();
      }
    };
  }
}
