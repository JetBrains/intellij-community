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
package com.jetbrains.python.inspections;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.util.Function;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.codeInsight.stdlib.PyStdlibUtil;
import com.jetbrains.python.packaging.*;
import com.jetbrains.python.packaging.ui.PyChooseRequirementsDialog;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.sdk.PySdkUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author vlan
 */
public class PyPackageRequirementsInspection extends PyInspection {
  private static final Logger LOG = Logger.getInstance(PyPackageRequirementsInspection.class);
  
  public JDOMExternalizableStringList ignoredPackages = new JDOMExternalizableStringList();

  @NotNull
  @Override
  public String getDisplayName() {
    return "Package requirements";
  }

  @Override
  public JComponent createOptionsPanel() {
    final ListEditForm form = new ListEditForm("Ignore packages", ignoredPackages);
    return form.getContentPanel();
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session, ignoredPackages);
  }

  @Nullable
  public static PyPackageRequirementsInspection getInstance(@NotNull PsiElement element) {
    final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(element.getProject()).getInspectionProfile();
    final String toolName = PyPackageRequirementsInspection.class.getSimpleName();
    return (PyPackageRequirementsInspection)inspectionProfile.getUnwrappedTool(toolName, element);
  }

  private static class Visitor extends PyInspectionVisitor {
    private final Set<String> myIgnoredPackages;

    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session, Collection<String> ignoredPackages) {
      super(holder, session);
      myIgnoredPackages = ImmutableSet.copyOf(ignoredPackages);
    }

    @Override
    public void visitPyFile(PyFile node) {
      final Module module = ModuleUtilCore.findModuleForPsiElement(node);
      if (module != null) {
        if (isRunningPackagingTasks(module)) {
          return;
        }
        final Sdk sdk = PythonSdkType.findPythonSdk(module);
        if (sdk != null) {
          final List<PyRequirement> unsatisfied = findUnsatisfiedRequirements(module, sdk, myIgnoredPackages);
          if (unsatisfied != null && !unsatisfied.isEmpty()) {
            final boolean plural = unsatisfied.size() > 1;
            String msg = String.format("Package requirement%s %s %s not satisfied",
                                       plural ? "s" : "",
                                       requirementsToString(unsatisfied),
                                       plural ? "are" : "is");
            final Set<String> unsatisfiedNames = new HashSet<String>();
            for (PyRequirement req : unsatisfied) {
              unsatisfiedNames.add(req.getName());
            }
            final List<LocalQuickFix> quickFixes = new ArrayList<LocalQuickFix>();
            quickFixes.add(new PyInstallRequirementsFix(null, module, sdk, unsatisfied));
            quickFixes.add(new IgnoreRequirementFix(unsatisfiedNames));
            registerProblem(node, msg,
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, null,
                            quickFixes.toArray(new LocalQuickFix[quickFixes.size()]));
          }
        }
      }
    }

    @Override
    public void visitPyFromImportStatement(PyFromImportStatement node) {
      final PyReferenceExpression expr = node.getImportSource();
      if (expr != null) {
        checkPackageNameInRequirements(expr);
      }
    }

    @Override
    public void visitPyImportStatement(PyImportStatement node) {
      for (PyImportElement element : node.getImportElements()) {
        final PyReferenceExpression expr = element.getImportReferenceExpression();
        if (expr != null) {
          checkPackageNameInRequirements(expr);
        }
      }
    }

    private void checkPackageNameInRequirements(@NotNull PyQualifiedExpression importedExpression) {
      for (PyInspectionExtension extension : Extensions.getExtensions(PyInspectionExtension.EP_NAME)) {
        if (extension.ignorePackageNameInRequirements(importedExpression)) {
          return;
        }
      }
      final PyExpression packageReferenceExpression = PyPsiUtils.getFirstQualifier(importedExpression);
      if (packageReferenceExpression != null) {
        final String packageName = packageReferenceExpression.getName();
        if (packageName != null && !myIgnoredPackages.contains(packageName)) {
          if (!ApplicationManager.getApplication().isUnitTestMode() && !PyPIPackageUtil.INSTANCE.isInPyPI(packageName)) {
            return;
          }
          final Collection<String> stdlibPackages = PyStdlibUtil.getPackages();
          if (stdlibPackages != null) {
            if (stdlibPackages.contains(packageName)) {
              return;
            }
          }
          if (PyPackageManager.SETUPTOOLS.equals(packageName)) {
            return;
          }
          final Module module = ModuleUtilCore.findModuleForPsiElement(packageReferenceExpression);
          if (module != null) {
            final Sdk sdk = PythonSdkType.findPythonSdk(module);
            if (sdk != null) {
              final PyPackageManager manager = PyPackageManager.getInstance(sdk);
              Collection<PyRequirement> requirements = manager.getRequirements(module);
              if (requirements != null) {
                requirements = getTransitiveRequirements(sdk, requirements, new HashSet<PyPackage>());
              }
              if (requirements == null) return;
              for (PyRequirement req : requirements) {
                if (packageName.equalsIgnoreCase(req.getName())) {
                  return;
                }
              }
              if (!ApplicationManager.getApplication().isUnitTestMode()) {
                final PsiReference reference = packageReferenceExpression.getReference();
                if (reference != null) {
                  final PsiElement element = reference.resolve();
                  if (element != null) {
                    final PsiFile file = element.getContainingFile();
                    if (file != null) {
                      final VirtualFile virtualFile = file.getVirtualFile();
                      if (ModuleUtilCore.moduleContainsFile(module, virtualFile, false)) {
                        return;
                      }
                    }
                  }
                }
              }
              final List<LocalQuickFix> quickFixes = new ArrayList<LocalQuickFix>();
              quickFixes.add(new AddToRequirementsFix(module, packageName, LanguageLevel.forElement(importedExpression)));
              quickFixes.add(new IgnoreRequirementFix(Collections.singleton(packageName)));
              registerProblem(packageReferenceExpression, String.format("Package '%s' is not listed in project requirements", packageName),
                              ProblemHighlightType.WEAK_WARNING, null,
                              quickFixes.toArray(new LocalQuickFix[quickFixes.size()]));
            }
          }
        }
      }
    }
  }

  @Nullable
  private static Set<PyRequirement> getTransitiveRequirements(@NotNull Sdk sdk, @NotNull Collection<PyRequirement> requirements,
                                                              @NotNull Set<PyPackage> visited) {
    if (requirements.isEmpty()) {
      return Collections.emptySet();
    }
    final Set<PyRequirement> results = new HashSet<PyRequirement>(requirements);
    final List<PyPackage> packages;
    try {
      packages = PyPackageManager.getInstance(sdk).getPackages(PySdkUtil.isRemote(sdk));
    }
    catch (ExecutionException e) {
      return null;
    }
    if (packages == null) return null;
    for (PyRequirement req : requirements) {
      final PyPackage pkg = req.match(packages);
      if (pkg != null && !visited.contains(pkg)) {
        visited.add(pkg);
        final Set<PyRequirement> transitive = getTransitiveRequirements(sdk, pkg.getRequirements(), visited);
        if (transitive == null) return null;
        results.addAll(transitive);
      }
    }
    return results;
  }

  @NotNull
  private static String requirementsToString(@NotNull List<PyRequirement> requirements) {
    return StringUtil.join(requirements, new Function<PyRequirement, String>() {
      @Override
      public String fun(PyRequirement requirement) {
        return String.format("'%s'", requirement.toString());
      }
    }, ", ");
  }

  @Nullable
  private static List<PyRequirement> findUnsatisfiedRequirements(@NotNull Module module, @NotNull Sdk sdk,
                                                                 @NotNull Set<String> ignoredPackages) {
    final PyPackageManager manager = PyPackageManager.getInstance(sdk);
    List<PyRequirement> requirements = manager.getRequirements(module);
    if (requirements != null) {
      final List<PyPackage> packages;
      try {
        packages = manager.getPackages(PySdkUtil.isRemote(sdk));
      }
      catch (ExecutionException e) {
        LOG.error(e);
        return null;
      }
      if (packages == null) return null;
      final List<PyRequirement> unsatisfied = new ArrayList<PyRequirement>();
      for (PyRequirement req : requirements) {
        if (!ignoredPackages.contains(req.getName()) && req.match(packages) == null) {
          unsatisfied.add(req);
        }
      }
      return unsatisfied;
    }
    return null;
  }

  private static void setRunningPackagingTasks(@NotNull Module module, boolean value) {
    module.putUserData(PyPackageManager.RUNNING_PACKAGING_TASKS, value);
  }

  private static boolean isRunningPackagingTasks(@NotNull Module module) {
    final Boolean value = module.getUserData(PyPackageManager.RUNNING_PACKAGING_TASKS);
    return value != null && value;
  }

  public static class PyInstallRequirementsFix implements LocalQuickFix {
    @NotNull private String myName;
    @NotNull private final Module myModule;
    @NotNull private Sdk mySdk;
    @NotNull private final List<PyRequirement> myUnsatisfied;

    public PyInstallRequirementsFix(@Nullable String name, @NotNull Module module, @NotNull Sdk sdk,
                                    @NotNull List<PyRequirement> unsatisfied) {
      final boolean plural = unsatisfied.size() > 1;
      myName = name != null ? name : String.format("Install requirement%s", plural ? "s" : "");
      myModule = module;
      mySdk = sdk;
      myUnsatisfied = unsatisfied;
    }

    @NotNull
    @Override
    public String getName() {
      return myName;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return myName;
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      boolean installManagement = false;
      final PyPackageManager manager = PyPackageManager.getInstance(mySdk);
      boolean hasManagement = false;
      try {
        hasManagement = manager.hasManagement(false);
      }
      catch (ExecutionException ignored) {
      }
      if (!hasManagement) {
        final int result = Messages.showYesNoDialog(project,
                                                    "Python packaging tools are required for installing packages. Do you want to " +
                                                    "install 'pip' and 'setuptools' for your interpreter?",
                                                    "Install Python Packaging Tools",
                                                    Messages.getQuestionIcon());
        if (result == Messages.YES) {
          installManagement = true;
        }
        else {
          return;
        }
      }
      final List<PyRequirement> chosen;
      if (myUnsatisfied.size() > 1) {
        final PyChooseRequirementsDialog dialog = new PyChooseRequirementsDialog(project, myUnsatisfied);
        if (dialog.showAndGet()) {
          chosen = dialog.getMarkedElements();
        }
        else {
          chosen = Collections.emptyList();
        }
      }
      else {
        chosen = myUnsatisfied;
      }
      if (chosen.isEmpty()) {
        return;
      }
      if (installManagement) {
        final PyPackageManagerUI ui = new PyPackageManagerUI(project, mySdk, new UIListener(myModule) {
          @Override
          public void finished(List<ExecutionException> exceptions) {
            super.finished(exceptions);
            if (exceptions.isEmpty()) {
              installRequirements(project, chosen);
            }
          }
        });
        ui.installManagement();
      }
      else {
        installRequirements(project, chosen);
      }
    }

    private void installRequirements(Project project, List<PyRequirement> requirements) {
      final PyPackageManagerUI ui = new PyPackageManagerUI(project, mySdk, new UIListener(myModule));
      ui.install(requirements, Collections.<String>emptyList());
    }
  }

  public static class InstallAndImportQuickFix implements LocalQuickFix {

    private final Sdk mySdk;
    private final Module myModule;
    private String myPackageName;
    @Nullable private final String myAsName;
    @NotNull private final SmartPsiElementPointer<PyElement> myNode;

    public InstallAndImportQuickFix(@NotNull final String packageName,
                                    @Nullable final String asName,
                                    @NotNull final PyElement node) {
      myPackageName = packageName;
      myAsName = asName;
      myNode = SmartPointerManager.getInstance(node.getProject()).createSmartPsiElementPointer(node, node.getContainingFile());
      myModule = ModuleUtilCore.findModuleForPsiElement(node);
      mySdk = PythonSdkType.findPythonSdk(myModule);
    }

    @NotNull
    public String getName() {
      return "Install and import package " + myPackageName;
    }

    @NotNull
    public String getFamilyName() {
      return "Install and import package " + myPackageName;
    }

    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      final PyPackageManagerUI ui = new PyPackageManagerUI(project, mySdk, new UIListener(myModule) {
        @Override
        public void finished(List<ExecutionException> exceptions) {
          super.finished(exceptions);
          if (exceptions.isEmpty()) {

            final PyElement element = myNode.getElement();
            if (element == null) return;

            CommandProcessor.getInstance().executeCommand(project, new Runnable() {
              @Override
              public void run() {
                ApplicationManager.getApplication().runWriteAction(new Runnable() {
                  @Override
                  public void run() {
                    AddImportHelper.addImportStatement(element.getContainingFile(), myPackageName, myAsName,
                                                       AddImportHelper.ImportPriority.THIRD_PARTY, element);
                  }
                });
              }
            }, "Add import", "Add import");
          }
        }
      });
      ui.install(Collections.singletonList(new PyRequirement(myPackageName)), Collections.<String>emptyList());
    }
  }

  private static class UIListener implements PyPackageManagerUI.Listener {
    private final Module myModule;

    public UIListener(Module module) {
      myModule = module;
    }

    @Override
    public void started() {
      setRunningPackagingTasks(myModule, true);
    }

    @Override
    public void finished(List<ExecutionException> exceptions) {
      setRunningPackagingTasks(myModule, false);
    }
  }


  private static class IgnoreRequirementFix implements LocalQuickFix {
    @NotNull private final Set<String> myPackageNames;

    public IgnoreRequirementFix(@NotNull Set<String> packageNames) {
      myPackageNames = packageNames;
    }

    @NotNull
    @Override
    public String getName() {
      final boolean plural = myPackageNames.size() > 1;
      return String.format("Ignore requirement%s", plural ? "s" : "");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element != null) {
        final PyPackageRequirementsInspection inspection = getInstance(element);
        if (inspection != null) {
          final JDOMExternalizableStringList ignoredPackages = inspection.ignoredPackages;
          boolean changed = false;
          for (String name : myPackageNames) {
            if (!ignoredPackages.contains(name)) {
              ignoredPackages.add(name);
              changed = true;
            }
          }
          if (changed) {
            final InspectionProfile profile = InspectionProjectProfileManager.getInstance(project).getInspectionProfile();
            InspectionProfileManager.getInstance().fireProfileChanged(profile);
          }
        }
      }
    }
  }

  private static class AddToRequirementsFix implements LocalQuickFix {
    @NotNull private final String myPackageName;
    @NotNull private final LanguageLevel myLanguageLevel;
    @NotNull private Module myModule;

    private AddToRequirementsFix(@NotNull Module module, @NotNull String packageName, @NotNull LanguageLevel languageLevel) {
      myPackageName = packageName;
      myLanguageLevel = languageLevel;
      myModule = module;
    }

    @Nullable
    private PyArgumentList findSetupArgumentList() {
      final PyFile setupPy = PyPackageUtil.findSetupPy(myModule);
      if (setupPy != null) {
        final PyCallExpression setupCall = PyPackageUtil.findSetupCall(setupPy);
        if (setupCall != null) {
          return setupCall.getArgumentList();
        }
      }
      return null;
    }

    @NotNull
    @Override
    public String getName() {
      final String target;
      final VirtualFile requirementsTxt = PyPackageUtil.findRequirementsTxt(myModule);
      final PyListLiteralExpression setupPyRequires = PyPackageUtil.findSetupPyRequires(myModule);
      if (requirementsTxt != null) {
        target = requirementsTxt.getName();
      }
      else if (setupPyRequires != null || findSetupArgumentList() != null) {
        target = "setup.py";
      }
      else {
        target = "project requirements";
      }
      return String.format("Add requirement '%s' to %s", myPackageName, target);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      final VirtualFile requirementsTxt = PyPackageUtil.findRequirementsTxt(myModule);
      final PyListLiteralExpression setupPyRequires = PyPackageUtil.findSetupPyRequires(myModule);
      CommandProcessor.getInstance().executeCommand(project, new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              if (requirementsTxt != null) {
                if (requirementsTxt.isWritable()) {
                  final Document document = FileDocumentManager.getInstance().getDocument(requirementsTxt);
                  if (document != null) {
                    document.insertString(0, myPackageName + "\n");
                  }
                }
              }
              else {
                final PyElementGenerator generator = PyElementGenerator.getInstance(project);
                final PyArgumentList argumentList = findSetupArgumentList();
                if (setupPyRequires != null) {
                  if (setupPyRequires.getContainingFile().isWritable()) {
                    final String text = String.format("'%s'", myPackageName);
                    final PyExpression generated = generator.createExpressionFromText(myLanguageLevel, text);
                    setupPyRequires.add(generated);
                  }
                }
                else if (argumentList != null) {
                  final PyKeywordArgument requiresArg = generateRequiresKwarg(generator);
                  if (requiresArg != null) {
                    argumentList.addArgument(requiresArg);
                  }
                }
              }
            }

            @Nullable
            private PyKeywordArgument generateRequiresKwarg(PyElementGenerator generator) {
              final String text = String.format("foo(requires=['%s'])", myPackageName);
              final PyExpression generated = generator.createExpressionFromText(myLanguageLevel, text);
              PyKeywordArgument installRequiresArg = null;
              if (generated instanceof PyCallExpression) {
                final PyCallExpression foo = (PyCallExpression)generated;
                for (PyExpression arg : foo.getArguments()) {
                  if (arg instanceof PyKeywordArgument) {
                    final PyKeywordArgument kwarg = (PyKeywordArgument)arg;
                    if ("requires".equals(kwarg.getKeyword())) {
                      installRequiresArg = kwarg;
                    }
                  }
                }
              }
              return installRequiresArg;
            }
          });
        }
      }, getName(), null);
    }
  }
}
