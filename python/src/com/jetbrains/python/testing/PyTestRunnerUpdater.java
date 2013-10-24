/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.jetbrains.python.testing;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonModuleTypeBase;
import com.jetbrains.python.documentation.DocStringFormat;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Detects test runner and docstring format
 *
 */
public class PyTestRunnerUpdater implements StartupActivity {
  @Override
  public void runActivity(@NotNull final Project project) {
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      return;
    }

    for (Module m: ModuleManager.getInstance(project).getModules()) {
      if (ModuleType.get(m) instanceof PythonModuleTypeBase) {
        updateIntegratedTools(m, 10000);
        break;
      }
    }
  }

  private static void updateIntegratedTools(final Module module, final int delay) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        if (delay > 0) {
          try {
            Thread.sleep(delay); // wait until all short-term disk-hitting activity ceases
          }
          catch (InterruptedException ignore) {
          }
        }

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            if (!TestRunnerService.getInstance(module).getProjectConfiguration().isEmpty())
              return;

            //check setup.py
            String testRunner = detectTestRunnerFromSetupPy(module);

            //try to find test_runner import
            final Collection<VirtualFile> filenames = FilenameIndex.getAllFilesByExt(module.getProject(), PythonFileType.INSTANCE.getDefaultExtension(),
                                                                                     GlobalSearchScope.moduleScope(module));

            for (VirtualFile file : filenames) {
              if (file.getName().startsWith("test")) {
                if (testRunner.isEmpty()) testRunner = checkImports(file, module);   //find test runner import
              }
              else {
                if (PyDocumentationSettings.getInstance(module).getFormat().isEmpty()) {
                  checkDocstring(file, module);    // detect docstring type
                }
              }
              if (!testRunner.isEmpty() && !PyDocumentationSettings.getInstance(module).getFormat().isEmpty()) {
                break;
              }
            }
            if (testRunner.isEmpty()) {
              //check if installed in sdk
              final Sdk sdk = PythonSdkType.findPythonSdk(module);
              if (sdk != null && sdk.getSdkType() instanceof PythonSdkType && testRunner.isEmpty()) {
                final Boolean nose = VFSTestFrameworkListener.isTestFrameworkInstalled(sdk, PyNames.NOSE_TEST);
                final Boolean pytest = VFSTestFrameworkListener.isTestFrameworkInstalled(sdk, PyNames.PY_TEST);
                final Boolean attest = VFSTestFrameworkListener.isTestFrameworkInstalled(sdk, PyNames.AT_TEST);
                if (nose != null && nose)
                  testRunner = PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME;
                else if (pytest != null && pytest)
                  testRunner = PythonTestConfigurationsModel.PY_TEST_NAME;
                else if (attest != null && attest)
                  testRunner = PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME;
              }
            }

            if (testRunner.isEmpty()) testRunner = PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME;

            TestRunnerService.getInstance(module).setProjectConfiguration(testRunner);
            if (PyDocumentationSettings.getInstance(module).getFormat().isEmpty())
              PyDocumentationSettings.getInstance(module).setFormat(DocStringFormat.PLAIN);
          }
        }, ModalityState.any(), module.getDisposed());
      }
    });
  }

  private static String detectTestRunnerFromSetupPy(Module module) {
    String testRunner = "";
    if (!testRunner.isEmpty()) return  testRunner;
    final PyFile setupPy = PyPackageUtil.findSetupPy(module);
    if (setupPy == null)
      return  testRunner;
    final PyCallExpression setupCall = PyPackageUtil.findSetupCall(setupPy);
    if (setupCall == null)
      return  testRunner;
    for (PyExpression arg : setupCall.getArguments()) {
      if (arg instanceof PyKeywordArgument) {
        final PyKeywordArgument kwarg = (PyKeywordArgument)arg;
        if ("test_loader".equals(kwarg.getKeyword()) || "test_suite".equals(kwarg.getKeyword())) {
          final PyExpression value = kwarg.getValueExpression();
          if (value instanceof PyStringLiteralExpression) {
            final String stringValue = ((PyStringLiteralExpression)value).getStringValue();
            if (stringValue.contains(PyNames.NOSE_TEST)) {
              testRunner = PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME;
              break;
            }
            if (stringValue.contains(PyNames.PY_TEST)) {
              testRunner = PythonTestConfigurationsModel.PY_TEST_NAME;
              break;
            }
            if (stringValue.contains(PyNames.AT_TEST_IMPORT)) {
              testRunner = PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME;
              break;
            }
          }
        }
      }
    }
    return testRunner;
  }

  private static void checkDocstring(VirtualFile file, Module module) {
    final PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(module);
    final PsiFile psiFile = PsiManager.getInstance(module.getProject()).findFile(file);
    if (psiFile instanceof PyFile) {
      if (documentationSettings.isEpydocFormat(psiFile))
        documentationSettings.setFormat(DocStringFormat.EPYTEXT);
      else if (documentationSettings.isReSTFormat(psiFile))
        documentationSettings.setFormat(DocStringFormat.REST);
      else {
        final String fileText = psiFile.getText();
        if (!fileText.contains(":param ") && !fileText.contains(":type ") && !fileText.contains(":rtype ") &&
            !fileText.contains("@param ") && !fileText.contains("@type ") && !fileText.contains("@rtype ")) return;

        final PyDocStringOwner[] childrens = PsiTreeUtil.getChildrenOfType(psiFile, PyDocStringOwner.class);
        if (childrens != null) {
          for (PyDocStringOwner owner : childrens) {
            final PyStringLiteralExpression docStringExpression = owner.getDocStringExpression();
            if (docStringExpression != null) {
              String text = docStringExpression.getStringValue();
              if (text.contains(":param ") || text.contains(":rtype") || text.contains(":type")) {
                documentationSettings.setFormat(DocStringFormat.REST);
                return;
              }
              else if (text.contains("@param ") || text.contains("@rtype") || text.contains("@type")) {
                documentationSettings.setFormat(DocStringFormat.EPYTEXT);
                return;
              }
            }
          }
        }
      }
    }
  }

  private static String checkImports(VirtualFile file, Module module) {
    final PsiFile psiFile = PsiManager.getInstance(module.getProject()).findFile(file);
    if (psiFile instanceof PyFile) {
      final List<PyImportElement> importTargets = ((PyFile)psiFile).getImportTargets();
      for (PyImportElement importElement : importTargets) {
        if (PyNames.NOSE_TEST.equals(importElement.getVisibleName())) {
          return PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME;
        }
        if (PyNames.PY_TEST.equals(importElement.getVisibleName())) {
          return PythonTestConfigurationsModel.PY_TEST_NAME;
        }
        if (PyNames.AT_TEST_IMPORT.equals(importElement.getVisibleName())) {
          return PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME;
        }
      }
    }
    return "";
  }
}
