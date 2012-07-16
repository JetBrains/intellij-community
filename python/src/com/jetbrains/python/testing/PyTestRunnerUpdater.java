package com.jetbrains.python.testing;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
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

    updateIntegratedTools(project, 10000);
  }

  private static void updateIntegratedTools(final Project project, final int delay) {
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      public void run() {
        if (delay > 0) {
          try {
            Thread.sleep(delay); // wait until all short-term disk-hitting activity ceases
          }
          catch (InterruptedException ignore) {
          }
        }

        if (!TestRunnerService.getInstance(project).getProjectConfiguration().isEmpty())
          return;

        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            String testRunner = "";
            //check setup.py
            final Module[] modules = ModuleManager.getInstance(project).getModules();
            for (Module module : modules) {
              if (!testRunner.isEmpty()) break;
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
                        testRunner = PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME;
                        break;
                      }
                      if (stringValue.contains(PyNames.PY_TEST)) {
                        testRunner = PythonTestConfigurationsModel.PY_TEST_NAME;
                        break;
                      }
                      if (stringValue.contains(PyNames.AT_TEST)) {
                        testRunner = PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME;
                        break;
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
                if (testRunner.isEmpty()) testRunner = checkImports(file, project);   //find test runner import
              }
              else {
                checkDocstring(file, project);    // detect docstring type
              }
            }
            if (testRunner.isEmpty()) {
              //check if installed in sdk
              for (Module module : ModuleManager.getInstance(project).getModules()) {
                final Sdk sdk = PythonSdkType.findPythonSdk(module);
                if (sdk != null && testRunner.isEmpty()) {
                  String sdkHome = sdk.getHomePath();
                  if (VFSTestFrameworkListener.isTestFrameworkInstalled(sdkHome, VFSTestFrameworkListener.NOSETESTSEARCHER))
                    testRunner = PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME;
                  else if (VFSTestFrameworkListener.isTestFrameworkInstalled(sdkHome, VFSTestFrameworkListener.PYTESTSEARCHER))
                    testRunner = PythonTestConfigurationsModel.PY_TEST_NAME;
                  else if (VFSTestFrameworkListener.isTestFrameworkInstalled(sdkHome, VFSTestFrameworkListener.ATTESTSEARCHER))
                    testRunner = PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME;
                }

              }
            }

            if (testRunner.isEmpty()) testRunner = PythonTestConfigurationsModel.PYTHONS_UNITTEST_NAME;

            TestRunnerService.getInstance(project).setProjectConfiguration(testRunner);
            if (PyDocumentationSettings.getInstance(project).getFormat().isEmpty())
              PyDocumentationSettings.getInstance(project).setFormat(DocStringFormat.PLAIN);
          }
        }, ModalityState.any(), project.getDisposed());
      }
    });
  }

  private static void checkDocstring(VirtualFile file, Project project) {
    final PyDocumentationSettings documentationSettings = PyDocumentationSettings.getInstance(project);
    if (!documentationSettings.getFormat().isEmpty()) return;
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
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

  private static String checkImports(VirtualFile file, Project project) {
    final PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile instanceof PyFile) {
      final List<PyImportElement> importTargets = ((PyFile)psiFile).getImportTargets();
      for (PyImportElement importElement : importTargets) {
        if (PyNames.NOSE_TEST.equals(importElement.getVisibleName())) {
          return PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME;
        }
        if (PyNames.PY_TEST.equals(importElement.getVisibleName())) {
          return PythonTestConfigurationsModel.PY_TEST_NAME;
        }
        if (PyNames.AT_TEST.equals(importElement.getVisibleName())) {
          return PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME;
        }
      }
    }
    return "";
  }
}
