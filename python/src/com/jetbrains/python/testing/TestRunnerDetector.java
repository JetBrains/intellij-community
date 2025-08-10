// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Function;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.packaging.management.PythonPackageManager;
import com.jetbrains.python.packaging.management.PythonPackageManagerExt;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.python.sdk.PythonSdkUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

final class TestRunnerDetector implements Function<Pair<Module, Collection<VirtualFile>>, TestRunnerService.ServiceState> {
  private static final Logger LOG = Logger.getInstance(TestRunnerDetector.class);

  @Override
  public TestRunnerService.ServiceState fun(@NotNull Pair<Module, Collection<VirtualFile>> pair) {
    Module module = pair.first;
    if (module.isDisposed()) {
      return null;
    }
    ApplicationManager.getApplication().assertIsNonDispatchThread();
    //  //check setup.py
    String testRunner = ReadAction.compute(() -> detectTestRunnerFromSetupPy(module));
    assert testRunner != null : "detectTestRunnerFromSetupPy can't return null";
    if (!testRunner.isEmpty()) {
      LOG.debug("Test runner '" + testRunner + "' was discovered from setup.py in the module '" + module.getName() + "'");
      return new TestRunnerService.ServiceState(testRunner);
    }

    final Collection<VirtualFile> pyFiles = pair.second;


    for (VirtualFile file : pyFiles) {
      if (file.getName().startsWith("test")) {
        //find test runner import
        testRunner = ReadAction.compute(() -> checkImports(file, module));
        if (!testRunner.isEmpty()) {
          LOG.debug("Test runner '" + testRunner + "' was detected from imports in the file '" + file.getPath() + "'");
          return new TestRunnerService.ServiceState(testRunner);
        }
      }
    }
    //check if installed in sdk
    final Sdk sdk = PythonSdkUtil.findPythonSdk(module);
    if (sdk != null && sdk.getSdkType() instanceof PythonSdkType) {
      PythonPackageManager packageManager = PythonPackageManager.Companion.forSdk(module.getProject(), sdk);
      PythonPackageManagerExt.waitInitBlocking(packageManager);
      var factories = PythonTestConfigurationType.getInstance().getTypedFactories();
      var factory = factories.stream().filter(o -> o.isFrameworkInstalled(module.getProject(), sdk)).findFirst();
      if (factory.isPresent()) {
        testRunner = factory.get().getId();
      }
    }
    if (!testRunner.isEmpty()) {
      LOG.debug("Test runner '" + testRunner + "' was detected from SDK " + sdk);
      return new TestRunnerService.ServiceState(testRunner);
    }
    return null;
  }

  private static @NotNull String checkImports(@NotNull VirtualFile file, @NotNull Module module) {
    ApplicationManager.getApplication().assertReadAccessAllowed();
    final PsiFile psiFile = PsiManager.getInstance(module.getProject()).findFile(file);
    if (psiFile instanceof PyFile) {
      final List<PyImportElement> importTargets = ((PyFile)psiFile).getImportTargets();
      for (PyImportElement importElement : importTargets) {
        String name = importElement.getVisibleName();
        if (name != null) {
          @NonNls String runnerId = findSdkByPackage(name);
          if (runnerId != null) return runnerId;
        }
      }
    }
    return "";
  }

  private static @Nullable @NonNls String findSdkByPackage(@NotNull String packageToFind) {
    for (var factory : PythonTestConfigurationType.getInstance().getTypedFactories()) {
      var packageRequired = factory.getPackageRequired();
      if (packageRequired != null && packageRequired.equals(packageToFind)) {
        return factory.getId();
      }
    }
    return null;
  }

  private static @NotNull String detectTestRunnerFromSetupPy(@NotNull Module module) {
    final PyCallExpression setupCall = PyPackageUtil.findSetupCall(module);
    if (setupCall == null) return "";
    for (String argumentName : Arrays.asList("test_loader", "test_suite")) {
      final PyExpression argumentValue = setupCall.getKeywordArgument(argumentName);
      if (argumentValue instanceof PyStringLiteralExpression) {
        final String stringValue = ((PyStringLiteralExpression)argumentValue).getStringValue();
        @NonNls String runnerId = findSdkByPackage(stringValue);
        if (runnerId != null) return runnerId;
      }
    }
    return "";
  }
}
