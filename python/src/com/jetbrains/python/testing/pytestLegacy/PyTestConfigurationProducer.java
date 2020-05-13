// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.testing.pytestLegacy;

import com.intellij.execution.Location;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.openapi.extensions.ExtensionNotApplicableException;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyPsiPackageUtil;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageVersionComparator;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.testing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.python.testing.PyTestLegacyInteropKt.isNewTestsModeEnabled;

public final class PyTestConfigurationProducer extends PythonTestLegacyConfigurationProducer<PyTestRunConfiguration> {
  public PyTestConfigurationProducer() {
    if (isNewTestsModeEnabled()) {
      throw ExtensionNotApplicableException.INSTANCE;
    }
  }

  @NotNull
  @Override
  public ConfigurationFactory getConfigurationFactory() {
    return PythonTestConfigurationType.getInstance().LEGACY_PYTEST_FACTORY;
  }

  @Override
  protected boolean setupConfigurationFromContext(@NotNull AbstractPythonLegacyTestRunConfiguration<PyTestRunConfiguration> configuration,
                                                  @NotNull ConfigurationContext context,
                                                  @NotNull Ref<PsiElement> sourceElement) {
    final PsiElement element = sourceElement.get();
    final Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (!(configuration instanceof PyTestRunConfiguration)) {
      return false;
    }
    if (module == null) {
      return false;
    }
    if (!(TestRunnerService.getInstance(module).getProjectConfiguration().equals(
      PyTestFrameworkService.getSdkReadableNameByFramework(PyNames.PY_TEST)))) {
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
      final List<PyStatement> testCases =
        getPyTestCasesFromFile(file, TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile()));
      if (testCases.isEmpty()) {
        return false;
      }
    }
    else {
      return false;
    }

    final Sdk sdk = PythonSdkUtil.findPythonSdk(context.getModule());
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
      configuration.setName("pytest in " + keywords);
    }
    else {
      configuration.setName("pytest in " + file.getName());
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
        final List<PyPackage> packages = PyPackageManager.getInstance(sdk).getPackages();
        final PyPackage pytestPackage = packages != null ? PyPsiPackageUtil.findPackage(packages, "pytest") : null;
        if (pytestPackage != null && PyPackageVersionComparator.getSTR_COMPARATOR().compare(pytestPackage.getVersion(), "2.3.3") >= 0) {
          keywords = pyClass.getName() + " and " + keywords;
        }
        else {
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
  public boolean isConfigurationFromContext(@NotNull AbstractPythonLegacyTestRunConfiguration configuration,
                                            @NotNull ConfigurationContext context) {
    final Location location = context.getLocation();
    if (location == null) return false;
    if (!(configuration instanceof PyTestRunConfiguration)) return false;
    final PsiElement element = location.getPsiElement();

    final PsiFileSystemItem file = element instanceof PsiDirectory ? (PsiDirectory)element : element.getContainingFile();
    if (file == null) return false;
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return false;

    if (file instanceof PyFile || file instanceof PsiDirectory) {
      final List<PyStatement> testCases =
        getPyTestCasesFromFile(file, TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile()));
      if (testCases.isEmpty()) return false;
    }
    else {
      return false;
    }

    final Sdk sdk = PythonSdkUtil.findPythonSdk(context.getModule());
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

  public static List<PyStatement> getPyTestCasesFromFile(PsiFileSystemItem file, @NotNull final TypeEvalContext context) {
    List<PyStatement> result = new ArrayList<>();
    if (file instanceof PyFile) {
      result = getResult((PyFile)file, context);
    }
    else if (file instanceof PsiDirectory) {
      for (PsiFile f : ((PsiDirectory)file).getFiles()) {
        if (f instanceof PyFile) {
          result.addAll(getResult((PyFile)f, context));
        }
      }
    }
    return result;
  }

  private static List<PyStatement> getResult(PyFile file, @NotNull final TypeEvalContext context) {
    List<PyStatement> result = new ArrayList<>();
    for (PyClass cls : file.getTopLevelClasses()) {
      if (PythonUnitTestDetectorsKt.isTestClass(cls, context)) {
        result.add(cls);
      }
    }
    for (PyFunction function : file.getTopLevelFunctions()) {
      if (PythonUnitTestDetectorsKt.isTestFunction(function)) {
        result.add(function);
      }
    }
    return result;
  }
}
