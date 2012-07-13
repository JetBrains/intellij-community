package com.jetbrains.python.testing;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Detects test runner
 *
 */
public class PyTestRunnerUpdater implements StartupActivity {

  public static PyTestRunnerUpdater getInstance() {
    final StartupActivity[] extensions = Extensions.getExtensions(StartupActivity.POST_STARTUP_ACTIVITY);
    for (StartupActivity extension : extensions) {
      if (extension instanceof PyTestRunnerUpdater) {
        return (PyTestRunnerUpdater)extension;
      }
    }
    throw new UnsupportedOperationException("could not find self");
  }

  @Override
  public void runActivity(@NotNull final Project project) {
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      return;
    }

    updateTestRunner(project, 10000);
  }

  private static void updateTestRunner(final Project project, final int delay) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        if (delay > 0) {
          try {
            Thread.sleep(delay); // wait until all short-term disk-hitting activity ceases
          }
          catch (InterruptedException ignore) {
          }
        }

        final TestRunnerService runnerService = TestRunnerService.getInstance(project);
        if (!runnerService.getProjectConfiguration().isEmpty())
          return;

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            //check setup.py
            final Module[] modules = ModuleManager.getInstance(project).getModules();
            for (Module module : modules) {
              final PyFile setupPy = PyPackageUtil.findSetupPy(module);
              if (setupPy == null)
                continue;
              final PyCallExpression setupCall = PyPackageUtil.findSetupCall(setupPy);
              if (setupCall == null)
                continue;
              for (PyExpression arg : setupCall.getArguments()) {
                if (arg instanceof PyKeywordArgument) {
                  final PyKeywordArgument kwarg = (PyKeywordArgument)arg;
                  if ("test_loader".equals(kwarg.getKeyword()) || "test_suite".equals(kwarg.getKeyword())) {
                    final PyExpression value = kwarg.getValueExpression();
                    if (value instanceof PyStringLiteralExpression) {
                      final String stringValue = ((PyStringLiteralExpression)value).getStringValue();
                      if (stringValue.contains(PyNames.NOSE_TEST)) {
                        runnerService.setProjectConfiguration(PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME);
                        return;
                      }
                      if (stringValue.contains(PyNames.PY_TEST)) {
                        runnerService.setProjectConfiguration(PythonTestConfigurationsModel.PY_TEST_NAME);
                        return;
                      }
                      if (stringValue.contains(PyNames.AT_TEST)) {
                        runnerService.setProjectConfiguration(PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME);
                        return;
                      }
                    }
                  }
                }
              }
            }

            //try to find test_runner import
            final Collection<VirtualFile> filenames = FilenameIndex.getAllFilesByExt(project, PythonFileType.INSTANCE.getDefaultExtension(),
                                                                                     GlobalSearchScope.projectScope(project));

            for (VirtualFile file : filenames){
              if (file.getName().startsWith("test")) {
                final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
                if (psiFile instanceof PyFile) {
                  final List<PyImportElement> importTargets = ((PyFile)psiFile).getImportTargets();
                  for (PyImportElement importElement : importTargets) {
                    if (PyNames.NOSE_TEST.equals(importElement.getText())) {
                      runnerService.setProjectConfiguration(PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME);
                      return;
                    }
                    if (PyNames.PY_TEST.equals(importElement.getText())) {
                      runnerService.setProjectConfiguration(PythonTestConfigurationsModel.PY_TEST_NAME);
                      return;
                    }
                    if (PyNames.AT_TEST.equals(importElement.getText())) {
                      runnerService.setProjectConfiguration(PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME);
                      return;
                    }
                  }
                }
              }
            }
            runnerService.setProjectConfiguration(PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME);

            final Sdk sdk = ProjectRootManager.getInstance(project).getProjectSdk();
            if (sdk != null) {
              String sdkHome = sdk.getHomePath();
              if (VFSTestFrameworkListener.isTestFrameworkInstalled(sdkHome, VFSTestFrameworkListener.NOSETESTSEARCHER))
                runnerService.setProjectConfiguration(PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME);
              else if (VFSTestFrameworkListener.isTestFrameworkInstalled(sdkHome, VFSTestFrameworkListener.PYTESTSEARCHER))
                runnerService.setProjectConfiguration(PythonTestConfigurationsModel.PY_TEST_NAME);
              else if (VFSTestFrameworkListener.isTestFrameworkInstalled(sdkHome, VFSTestFrameworkListener.ATTESTSEARCHER))
                runnerService.setProjectConfiguration(PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME);
            }
          }
        }, ModalityState.any());
      }
    });
  }
}
