// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ui.ListEditForm;
import com.intellij.execution.ExecutionException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.codeInsight.stdlib.PyStdlibUtil;
import com.jetbrains.python.packaging.*;
import com.jetbrains.python.packaging.ui.PyChooseRequirementsDialog;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.sdk.PythonSdkType;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author vlan
 */
public class PyPackageRequirementsInspection extends PyInspection {
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
    if (!(holder.getFile() instanceof PyFile) && !(holder.getFile() instanceof PsiPlainTextFile)) return PsiElementVisitor.EMPTY_VISITOR;
    return new Visitor(holder, session, ignoredPackages);
  }

  @Nullable
  public static PyPackageRequirementsInspection getInstance(@NotNull PsiElement element) {
    final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(element.getProject()).getCurrentProfile();
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
      checkPackagesHaveBeenInstalled(node, ModuleUtilCore.findModuleForPsiElement(node));
    }

    @Override
    public void visitPlainTextFile(PsiPlainTextFile file) {
      final Module module = ModuleUtilCore.findModuleForPsiElement(file);
      if (module != null && file.getVirtualFile().equals(PyPackageUtil.findRequirementsTxt(module))) {
        checkPackagesHaveBeenInstalled(file, module);
      }
    }

    private void checkPackagesHaveBeenInstalled(@NotNull PsiElement file, @Nullable Module module) {
      if (module != null && !isRunningPackagingTasks(module)) {
        final Sdk sdk = PythonSdkType.findPythonSdk(module);
        if (sdk != null) {
          final List<PyRequirement> unsatisfied = findUnsatisfiedRequirements(module, sdk, myIgnoredPackages);
          if (unsatisfied != null && !unsatisfied.isEmpty()) {
            final boolean plural = unsatisfied.size() > 1;
            String msg = String.format("Package requirement%s %s %s not satisfied",
                                       plural ? "s" : "",
                                       PyPackageUtil.requirementsToString(unsatisfied),
                                       plural ? "are" : "is");
            final Set<String> unsatisfiedNames = new HashSet<>();
            for (PyRequirement req : unsatisfied) {
              unsatisfiedNames.add(req.getFullName());
            }
            final List<LocalQuickFix> quickFixes = new ArrayList<>();
            quickFixes.add(new PyInstallRequirementsFix(null, module, sdk, unsatisfied));
            quickFixes.add(new IgnoreRequirementFix(unsatisfiedNames));
            registerProblem(file, msg,
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

      final String packageName = packageReferenceExpression.getName();
      if (packageName != null && !myIgnoredPackages.contains(packageName)) {
        final List<String> possiblePyPIPackageNames = PyPIPackageUtil.PACKAGES_TOPLEVEL.getOrDefault(packageName, Collections.emptyList());

        if (!ApplicationManager.getApplication().isUnitTestMode() &&
            !PyPIPackageUtil.INSTANCE.isInPyPI(packageName) &&
            !ContainerUtil.exists(possiblePyPIPackageNames, PyPIPackageUtil.INSTANCE::isInPyPI)) return;

        if (PyPackageUtil.SETUPTOOLS.equals(packageName)) return;

        final Collection<String> stdlibPackages = PyStdlibUtil.getPackages();
        if (stdlibPackages != null && stdlibPackages.contains(packageName)) return;

        final Module module = ModuleUtilCore.findModuleForPsiElement(packageReferenceExpression);
        if (module == null) return;

        final Collection<PyRequirement> requirements = getRequirementsInclTransitive(module);
        if (requirements == null) return;

        for (PyRequirement req : requirements) {
          final String name = req.getName();
          if (name.equalsIgnoreCase(packageName) || ContainerUtil.exists(possiblePyPIPackageNames, name::equalsIgnoreCase)) {
            return;
          }
          final String nameWhereUnderscoreReplacedWithHyphen = name.replaceAll("_", "-");
          if (ContainerUtil.exists(possiblePyPIPackageNames, nameWhereUnderscoreReplacedWithHyphen::equalsIgnoreCase)) {
            return;
          }
          final String nameWhereHyphenReplacedWithUnderscore = name.replaceAll("-", "_");
          if (nameWhereHyphenReplacedWithUnderscore.equalsIgnoreCase(packageName) ||
              ContainerUtil.exists(possiblePyPIPackageNames, nameWhereHyphenReplacedWithUnderscore::equalsIgnoreCase)) {
            return;
          }
        }

        if (!ApplicationManager.getApplication().isUnitTestMode()) {
          final PsiReference reference = packageReferenceExpression.getReference();
          if (reference != null) {
            final PsiElement element = reference.resolve();
            if (element instanceof PsiDirectory &&
                ModuleUtilCore.moduleContainsFile(module, ((PsiDirectory)element).getVirtualFile(), false)) {
              return;
            }
            else if (element != null) {
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

        final List<LocalQuickFix> quickFixes = new ArrayList<>();

        StreamEx
          .of(packageName)
          .append(possiblePyPIPackageNames)
          .filter(PyPIPackageUtil.INSTANCE::isInPyPI)
          .map(name -> new AddToRequirementsFix(module, name, LanguageLevel.forElement(importedExpression)))
          .forEach(quickFixes::add);

        quickFixes.add(new IgnoreRequirementFix(Collections.singleton(packageName)));

        registerProblem(packageReferenceExpression,
                        String.format("Package containing module '%s' is not listed in project requirements", packageName),
                        ProblemHighlightType.WEAK_WARNING,
                        null,
                        quickFixes.toArray(new LocalQuickFix[quickFixes.size()]));
      }
    }
  }

  @Nullable
  private static Set<PyRequirement> getRequirementsInclTransitive(@NotNull Module module) {
    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    if (sdk == null) return null;

    final List<PyRequirement> requirements = PyPackageManager.getInstance(sdk).getRequirements(module);
    if (requirements == null) return null;
    if (requirements.isEmpty()) return Collections.emptySet();

    final List<PyPackage> packages = PyPackageManager.getInstance(sdk).getPackages();
    if (packages == null) return null;

    final Set<PyRequirement> result = new HashSet<>(requirements);
    result.addAll(getTransitiveRequirements(packages, requirements, new HashSet<>()));
    return result;
  }

  @NotNull
  private static Set<PyRequirement> getTransitiveRequirements(@NotNull List<PyPackage> packages,
                                                              @NotNull Collection<PyRequirement> requirements,
                                                              @NotNull Set<PyPackage> visited) {
    final Set<PyRequirement> result = new HashSet<>();

    for (PyRequirement req : requirements) {
      final PyPackage pkg = req.match(packages);
      if (pkg != null && visited.add(pkg)) {
        result.addAll(getTransitiveRequirements(packages, pkg.getRequirements(), visited));
      }
    }

    return result;
  }

  @Nullable
  private static List<PyRequirement> findUnsatisfiedRequirements(@NotNull Module module, @NotNull Sdk sdk,
                                                                 @NotNull Set<String> ignoredPackages) {
    final PyPackageManager manager = PyPackageManager.getInstance(sdk);
    final List<PyRequirement> requirements = manager.getRequirements(module);
    if (requirements != null) {
      final List<PyPackage> packages = manager.getPackages();
      if (packages == null) {
        return null;
      }
      final List<PyPackage> packagesInModule = collectPackagesInModule(module);
      final List<PyRequirement> unsatisfied = new ArrayList<>();
      for (PyRequirement req : requirements) {
        if (!ignoredPackages.contains(req.getName()) && req.match(packages) == null && req.match(packagesInModule) == null) {
          unsatisfied.add(req);
        }
      }
      return unsatisfied;
    }
    return null;
  }

  @NotNull
  private static List<PyPackage> collectPackagesInModule(@NotNull Module module) {
    final String[] metadataExtensions = {"egg-info", "dist-info"};
    final List<PyPackage> result = new SmartList<>();

    for (VirtualFile srcRoot : PyUtil.getSourceRoots(module)) {
      for (VirtualFile metadata : VfsUtil.getChildren(srcRoot, file -> ArrayUtil.contains(file.getExtension(), metadataExtensions))) {
        final String[] nameAndVersionAndRest = metadata.getNameWithoutExtension().split("-", 3);
        if (nameAndVersionAndRest.length >= 2) {
          result.add(new PyPackage(nameAndVersionAndRest[0], nameAndVersionAndRest[1], null, Collections.emptyList()));
        }
      }
    }

    return result;
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
    public String getFamilyName() {
      return myName;
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      boolean installManagement = false;
      final PyPackageManager manager = PyPackageManager.getInstance(mySdk);
      final List<PyPackage> packages = manager.getPackages();
      if (packages == null) {
        return;
      }
      if (!PyPackageUtil.hasManagement(packages)) {
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
      ui.install(requirements, Collections.emptyList());
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

    @Nls
    @NotNull
    @Override
    public String getName() {
      return PyBundle.message("QFIX.NAME.install.and.import.package", myPackageName);
    }
    
    @Override
    @NotNull
    public String getFamilyName() {
      return PyBundle.message("QFIX.install.and.import.package");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
      final PyPackageManagerUI ui = new PyPackageManagerUI(project, mySdk, new UIListener(myModule) {
        @Override
        public void finished(List<ExecutionException> exceptions) {
          super.finished(exceptions);
          if (exceptions.isEmpty()) {

            final PyElement element = myNode.getElement();
            if (element == null) return;

            CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> {
              AddImportHelper.addImportStatement(element.getContainingFile(), myPackageName, myAsName,
                                                 AddImportHelper.ImportPriority.THIRD_PARTY, element);
            }), "Add import", "Add import");
          }
        }
      });
      ui.install(Collections.singletonList(new PyRequirement(myPackageName)), Collections.emptyList());
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
    public String getFamilyName() {
      final boolean plural = myPackageNames.size() > 1;
      return String.format("Ignore requirement%s", plural ? "s" : "");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
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
            ProjectInspectionProfileManager.getInstance(project).fireProfileChanged();
          }
        }
      }
    }
  }

  private static class AddToRequirementsFix implements LocalQuickFix {
    @NotNull private final Module myModule;
    @NotNull private final String myPackageName;
    @NotNull private final LanguageLevel myLanguageLevel;

    private AddToRequirementsFix(@NotNull Module module, @NotNull String packageName, @NotNull LanguageLevel languageLevel) {
      myModule = module;
      myPackageName = packageName;
      myLanguageLevel = languageLevel;
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return String.format("Add requirement '%s' to %s", myPackageName, calculateTarget());
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      CommandProcessor.getInstance().executeCommand(project, () -> ApplicationManager.getApplication().runWriteAction(() -> PyPackageUtil.addRequirementToTxtOrSetupPy(myModule, myPackageName, myLanguageLevel)), getName(), null);
    }

    @NotNull
    private String calculateTarget() {
      final VirtualFile requirementsTxt = PyPackageUtil.findRequirementsTxt(myModule);
      if (requirementsTxt != null) {
        return requirementsTxt.getName();
      }
      else if (PyPackageUtil.findSetupCall(myModule) != null) {
        return "setup.py";
      }
      else {
        return "project requirements";
      }
    }
  }
}
