/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.toolbox;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.fixtures.PyTestCase;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonConfigurationType;
import com.jetbrains.python.run.PythonRunConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Mikhail Golubev
 */
public class PyRunConfigurationTest extends PyTestCase {
  
  public void testPythonPathPreservesAdditionOrderOfSourceRoots() {
    myFixture.copyDirectoryToProject(getTestName(false), "");
    final VirtualFile sourceRoot1 = myFixture.findFileInTempDir("src1");
    final VirtualFile sourceRoot2 = myFixture.findFileInTempDir("src2");
    
    final Module module = myFixture.getModule();
    PsiTestUtil.addSourceRoot(module, sourceRoot1);
    PsiTestUtil.addSourceRoot(module, sourceRoot2);
    
    final ConfigurationFactory factory = PythonConfigurationType.getInstance().getConfigurationFactories()[0];
    final RunnerAndConfigurationSettings settings = RunManager.getInstance(myFixture.getProject()).createRunConfiguration("test", factory);
    final PythonRunConfiguration configuration = (PythonRunConfiguration)settings.getConfiguration();
    configuration.setAddSourceRoots(true);

    Collection<String> path = collectProjectPythonPathEntries(configuration);
    assertContainsOrdered(path, "/src", "/src/src1", "/src/src2");

    PsiTestUtil.removeSourceRoot(module, sourceRoot1);
    PsiTestUtil.addSourceRoot(module, sourceRoot1);

    path = collectProjectPythonPathEntries(configuration);
    assertContainsOrdered(path, "/src", "/src/src2", "/src/src1");
  }

  @NotNull
  private Collection<String> collectProjectPythonPathEntries(@NotNull PythonRunConfiguration configuration) {
    final Collection<String> roots = PythonCommandLineState.collectPythonPath(myFixture.getProject(), configuration, false);
    return ContainerUtil.map(roots, PathUtil::toSystemIndependentName);
  }

  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/runConfig/";
  }
}
