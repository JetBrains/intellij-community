// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.toolbox;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonConfigurationType;
import com.jetbrains.python.run.PythonRunConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Mikhail Golubev
 */
public class PyRunConfigurationTest extends CodeInsightFixtureTestCase {

  public void testPythonPathPreservesAdditionOrderOfSourceRoots() {
    myFixture.copyDirectoryToProject(getTestName(false), "");
    final String root = PathUtil.toSystemIndependentName(myFixture.getTempDirPath());
    final VirtualFile sourceRoot1 = myFixture.findFileInTempDir("src1");
    final VirtualFile sourceRoot2 = myFixture.findFileInTempDir("src2");

    final Module module = myFixture.getModule();
    PsiTestUtil.addSourceRoot(module, sourceRoot1);
    PsiTestUtil.addSourceRoot(module, sourceRoot2);

    final RunnerAndConfigurationSettings settings = RunManager.getInstance(myFixture.getProject()).createConfiguration("test", PythonConfigurationType.class);
    final PythonRunConfiguration configuration = (PythonRunConfiguration)settings.getConfiguration();
    configuration.setAddSourceRoots(true);

    Collection<String> path = collectProjectPythonPathEntries(configuration);
    assertContainsOrdered(path, root, root + "/src1", root + "/src2");

    PsiTestUtil.removeSourceRoot(module, sourceRoot1);
    PsiTestUtil.addSourceRoot(module, sourceRoot1);

    path = collectProjectPythonPathEntries(configuration);
    assertContainsOrdered(path, root, root + "/src2", root + "/src1");
  }

  @NotNull
  private Collection<String> collectProjectPythonPathEntries(@NotNull PythonRunConfiguration configuration) {
    final Collection<String> roots = PythonCommandLineState.collectPythonPath(myFixture.getProject(), configuration, false);
    return ContainerUtil.map(roots, PathUtil::toSystemIndependentName);
  }

  @Override
  protected boolean isCommunity() {
    return true;
  }

  @Override
  protected String getBasePath() {
    return "/python/testData/runConfig/";
  }
}
