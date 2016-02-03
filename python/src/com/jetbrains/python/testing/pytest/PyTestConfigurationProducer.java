/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.testing.pytest;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.webcore.packaging.PackageVersionComparator;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.testing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public class PyTestConfigurationProducer extends PythonTestConfigurationProducer {

  public PyTestConfigurationProducer() {
    super(PythonTestConfigurationType.getInstance().PY_PYTEST_FACTORY);
  }

  @Override
  protected boolean setupConfigurationFromContext(AbstractPythonTestRunConfiguration configuration,
                                                  ConfigurationContext context,
                                                  Ref<PsiElement> sourceElement) {
    final PsiElement element = sourceElement.get();
    final Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (!(configuration instanceof PyTestRunConfiguration)) {
      return false;
    }
    if (module == null) {
      return false;
    }
    if (!(TestRunnerService.getInstance(module).getProjectConfiguration().equals(
      PythonTestConfigurationsModel.PY_TEST_NAME))) {
      return false;
    }

    final PsiFileSystemItem file = element instanceof PsiDirectory ? (PsiDirectory)element : element.getContainingFile();
    if (file == null) {
      return false;
    }
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return false;
    }

    if (file instanceof PyFile || file instanceof PsiDirectory) {
      final List<PyStatement> testCases = PyTestUtil.getPyTestCasesFromFile(file, TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile()));
      if (testCases.isEmpty()) {
        return false;
      }
    }
    else {
      return false;
    }

    final Sdk sdk = PythonSdkType.findPythonSdk(context.getModule());
    if (sdk == null) {
      return false;
    }

    configuration.setUseModuleSdk(true);
    configuration.setModule(ModuleUtilCore.findModuleForPsiElement(element));
    ((PyTestRunConfiguration)configuration).setTestToRun(virtualFile.getPath());

    final String keywords = getKeywords(element, sdk);
    if (keywords != null) {
      ((PyTestRunConfiguration)configuration).useKeyword(true);
      ((PyTestRunConfiguration)configuration).setKeywords(keywords);
      configuration.setName("py.test in " + keywords);
    }
    else {
      configuration.setName("py.test in " + file.getName());
    }
    return true;
  }

  @Nullable
  private static String getKeywords(@NotNull final PsiElement element, @NotNull final Sdk sdk) {
    final PyFunction pyFunction = findTestFunction(element);
    final PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    String keywords = null;
    if (pyFunction != null) {
      keywords = pyFunction.getName();
      if (pyClass != null) {
        final PyPackageManager packageManager = PyPackageManager.getInstance(sdk);
        try {
          final PyPackage pytestPackage = packageManager.findPackage("pytest", false);
          if (pytestPackage != null && PackageVersionComparator.VERSION_COMPARATOR.compare(pytestPackage.getVersion(), "2.3.3") >= 0) {
            keywords = pyClass.getName() + " and " + keywords;
          }
          else {
            keywords = pyClass.getName() + "." + keywords;
          }
        }
        catch (ExecutionException e) {
          keywords = pyClass.getName() + "." + keywords;
        }
      }
    }
    else if (pyClass != null) {
      keywords = pyClass.getName();
    }
    return keywords;
  }

  @Nullable
  private static PyFunction findTestFunction(PsiElement element) {
    final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (function != null) {
      final String name = function.getName();
      if (name != null && name.startsWith("test")) {
        return function;
      }
    }
    return null;
  }

  @Override
  public boolean isConfigurationFromContext(AbstractPythonTestRunConfiguration configuration, ConfigurationContext context) {
    final Location location = context.getLocation();
    if (location == null) return false;
    if (!(configuration instanceof PyTestRunConfiguration)) return false;
    final PsiElement element = location.getPsiElement();

    final PsiFileSystemItem file = element instanceof PsiDirectory ? (PsiDirectory)element : element.getContainingFile();
    if (file == null) return false;
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return false;

    if (file instanceof PyFile || file instanceof PsiDirectory) {
      final List<PyStatement> testCases = PyTestUtil.getPyTestCasesFromFile(file, TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile()));
      if (testCases.isEmpty()) return false;
    }
    else {
      return false;
    }

    final Sdk sdk = PythonSdkType.findPythonSdk(context.getModule());
    if (sdk == null) return false;
    final String keywords = getKeywords(element, sdk);
    final String scriptName = ((PyTestRunConfiguration)configuration).getTestToRun();
    final String workingDirectory = configuration.getWorkingDirectory();
    final String path = virtualFile.getPath();
    final boolean isTestFileEquals = scriptName.equals(path) ||
                                     path.equals(new File(workingDirectory, scriptName).getAbsolutePath());

    final String configurationKeywords = ((PyTestRunConfiguration)configuration).getKeywords();
    return isTestFileEquals && (configurationKeywords.equals(keywords) ||
                                StringUtil.isEmptyOrSpaces(((PyTestRunConfiguration)configuration).getKeywords()) && keywords == null);
  }
}