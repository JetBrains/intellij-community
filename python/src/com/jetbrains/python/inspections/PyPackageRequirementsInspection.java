// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.google.common.collect.ImmutableSet;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsAction;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.core.CoreBundle;
import com.intellij.execution.ExecutionException;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.idea.ActionsBundle;
import com.intellij.notification.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMExternalizableStringList;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.*;
import com.intellij.python.community.impl.requirements.RequirementsFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PyPsiPackageUtil;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.codeInsight.stdlib.PyStdlibUtil;
import com.jetbrains.python.packaging.*;
import com.jetbrains.python.packaging.ui.PyChooseRequirementsDialog;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.PySdkExtKt;
import com.jetbrains.python.sdk.PySdkProvider;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.ui.PyUiUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.codeInspection.options.OptPane.pane;

public final class PyPackageRequirementsInspection extends PyInspection {
  public JDOMExternalizableStringList ignoredPackages = new JDOMExternalizableStringList();

  @NotNull
  private static final NotificationGroup BALLOON_NOTIFICATIONS = NotificationGroupManager.getInstance().getNotificationGroup("Package requirements");

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(OptPane.stringList("ignoredPackages", PyPsiBundle.message("INSP.requirements.ignore.packages.label")));
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    if (!(holder.getFile() instanceof PyFile) && !(holder.getFile() instanceof RequirementsFile)
        && !isPythonInTemplateLanguages(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new Visitor(holder, ignoredPackages, PyInspectionVisitor.getContext(session));
  }

  private static boolean isPythonInTemplateLanguages(PsiFile psiFile) {
    return StreamEx.of(psiFile.getViewProvider().getLanguages())
      .findFirst(x -> x.isKindOf(PythonLanguage.getInstance()))
      .isPresent();
  }

  @Nullable
  public static PyPackageRequirementsInspection getInstance(@NotNull PsiElement element) {
    final InspectionProfile inspectionProfile = InspectionProjectProfileManager.getInstance(element.getProject()).getCurrentProfile();
    final String toolName = PyPackageRequirementsInspection.class.getSimpleName();
    return (PyPackageRequirementsInspection)inspectionProfile.getUnwrappedTool(toolName, element);
  }

  private static class Visitor extends PyInspectionVisitor {
    private final Set<String> myIgnoredPackages;

    Visitor(@Nullable ProblemsHolder holder,
            Collection<String> ignoredPackages,
            @NotNull TypeEvalContext context) {
      super(holder, context);
      myIgnoredPackages = ImmutableSet.copyOf(ignoredPackages);
    }

    @Override
    public void visitPyFile(@NotNull PyFile node) {
      checkPackagesHaveBeenInstalled(node, ModuleUtilCore.findModuleForPsiElement(node));
    }

    @Override
    public void visitFile(@NotNull PsiFile file) {
      if (file instanceof RequirementsFile) {
        final Module module = ModuleUtilCore.findModuleForPsiElement(file);
        if (module != null && file.getVirtualFile().equals(PyPackageUtil.findRequirementsTxt(module))) {
          if (file.getText().trim().isEmpty()) {
            registerProblem(file, PyPsiBundle.message("INSP.package.requirements.requirements.file.empty"),
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, null, new PyGenerateRequirementsFileQuickFix(module));
          }
          else {
            checkPackagesHaveBeenInstalled(file, module);
          }
        }
      }
    }

    private void checkPackagesHaveBeenInstalled(@NotNull PsiElement file, @Nullable Module module) {
      if (module != null && !isRunningPackagingTasks(module)) {
        final Sdk sdk = PythonSdkUtil.findPythonSdk(module);
        if (sdk != null) {
          final List<PyRequirement> unsatisfied = findUnsatisfiedRequirements(module, sdk, myIgnoredPackages);
          if (unsatisfied != null && !unsatisfied.isEmpty()) {
            @NlsSafe String requirementsList = PyPackageUtil.requirementsToString(unsatisfied);
            @InspectionMessage String msg = PyPsiBundle.message("INSP.requirements.package.requirements.not.satisfied",
                                                                requirementsList, unsatisfied.size());
            final List<LocalQuickFix> quickFixes = new ArrayList<>();

            Optional<LocalQuickFix> providedFix = PySdkProvider.EP_NAME.getExtensionList().stream()
              .map(ext -> ext.createInstallPackagesQuickFix(module))
              .filter(fix -> fix != null)
              .findFirst();

            if (providedFix.isPresent()) {
              quickFixes.add(providedFix.get());
            }
            else {
              quickFixes.add(new PyInstallRequirementsFix(null, module, sdk, unsatisfied));
            }
            quickFixes.add(new IgnoreRequirementFix(ContainerUtil.map2Set(unsatisfied, PyRequirement::getName)));
            registerProblem(file, msg,
                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, null,
                            quickFixes.toArray(LocalQuickFix.EMPTY_ARRAY));
          }
        }
      }
    }

    @Override
    public void visitPyFromImportStatement(@NotNull PyFromImportStatement node) {
      final PyReferenceExpression expr = node.getImportSource();
      if (expr != null) {
        checkPackageNameInRequirements(expr);
      }
    }

    @Override
    public void visitPyImportStatement(@NotNull PyImportStatement node) {
      for (PyImportElement element : node.getImportElements()) {
        final PyReferenceExpression expr = element.getImportReferenceExpression();
        if (expr != null) {
          checkPackageNameInRequirements(expr);
        }
      }
    }

    private void checkPackageNameInRequirements(@NotNull PyQualifiedExpression importedExpression) {
      for (PyInspectionExtension extension : PyInspectionExtension.EP_NAME.getExtensionList()) {
        if (extension.ignorePackageNameInRequirements(importedExpression)) {
          return;
        }
      }

      final PyExpression packageReferenceExpression = PyPsiUtils.getFirstQualifier(importedExpression);

      final String packageName = packageReferenceExpression.getName();
      if (packageName != null && !myIgnoredPackages.contains(packageName)) {
        final String possiblePyPIPackageNames = PyPsiPackageUtil.PACKAGES_TOPLEVEL.getOrDefault(packageName, "");

        if (!ApplicationManager.getApplication().isUnitTestMode() &&
            !PyPIPackageUtil.INSTANCE.isInPyPI(packageName) &&
            !PyPIPackageUtil.INSTANCE.isInPyPI(possiblePyPIPackageNames)) return;

        if (PyPackageUtil.SETUPTOOLS.equals(packageName)) return;

        final Collection<String> stdlibPackages = PyStdlibUtil.getPackages();
        if (stdlibPackages != null && stdlibPackages.contains(packageName)) return;

        final Module module = ModuleUtilCore.findModuleForPsiElement(packageReferenceExpression);
        if (module == null) return;

        final Sdk sdk = PythonSdkUtil.findPythonSdk(module);
        if (sdk == null) return;

        final PyPackageManager packageManager = PyPackageManager.getInstance(sdk);

        final Collection<PyRequirement> requirements = getRequirementsInclTransitive(packageManager, module);
        if (requirements == null) return;

        for (PyRequirement req : requirements) {
          final String name = req.getName();
          if (name.equalsIgnoreCase(packageName) || name.equalsIgnoreCase(possiblePyPIPackageNames)) {
            return;
          }
          final String nameWhereUnderscoreReplacedWithHyphen = name.replaceAll("_", "-");
          if (nameWhereUnderscoreReplacedWithHyphen.equalsIgnoreCase(possiblePyPIPackageNames)) {
            return;
          }
          final String nameWhereHyphenReplacedWithUnderscore = name.replaceAll("-", "_");
          if (nameWhereHyphenReplacedWithUnderscore.equalsIgnoreCase(packageName) ||
              nameWhereHyphenReplacedWithUnderscore.equalsIgnoreCase(possiblePyPIPackageNames)) {
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

        final LocalQuickFix[] fixes = { new PyGenerateRequirementsFileQuickFix(module),
                                        new IgnoreRequirementFix(Collections.singleton(packageName))};

        registerProblem(packageReferenceExpression,
                        PyPsiBundle.message("INSP.requirements.package.containing.module.not.listed.in.project.requirements", packageName),
                        ProblemHighlightType.WEAK_WARNING,
                        null,
                        fixes);
      }
    }
  }

  @Nullable
  private static Set<PyRequirement> getRequirementsInclTransitive(@NotNull PyPackageManager packageManager, @NotNull Module module) {
    final List<PyRequirement> requirements = getListedRequirements(packageManager, module);
    if (requirements == null) return null;
    if (requirements.isEmpty()) return Collections.emptySet();

    final List<PyPackage> packages = packageManager.getPackages();
    if (packages == null) return null;

    final Set<PyRequirement> result = new HashSet<>(requirements);
    result.addAll(getTransitiveRequirements(packages, requirements, new HashSet<>()));
    return result;
  }

  @Nullable
  private static List<PyRequirement> getListedRequirements(@NotNull PyPackageManager packageManager, @NotNull Module module) {
    final List<PyRequirement> requirements = packageManager.getRequirements(module);
    final List<PyRequirement> extrasRequirements = getExtrasRequirements(module);
    if (requirements == null) return extrasRequirements;
    if (extrasRequirements == null) return requirements;
    return ContainerUtil.concat(requirements, extrasRequirements);
  }

  @Nullable
  private static List<PyRequirement> getExtrasRequirements(@NotNull Module module) {
    final Map<String, List<PyRequirement>> extrasRequire = PyPackageUtil.findSetupPyExtrasRequire(module);
    return extrasRequire == null ? null : ContainerUtil.flatten(extrasRequire.values());
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
          result.add(new PyPackage(nameAndVersionAndRest[0], nameAndVersionAndRest[1]));
        }
      }
    }

    return result;
  }

  private static void setRunningPackagingTasks(@NotNull Module module, boolean value) {
    module.putUserData(PyPackageManager.RUNNING_PACKAGING_TASKS, value);
  }

  public static boolean isRunningPackagingTasks(@NotNull Module module) {
    final Boolean value = module.getUserData(PyPackageManager.RUNNING_PACKAGING_TASKS);
    return value != null && value;
  }

  private static boolean checkAdminPermissionsAndConfigureInterpreter(@NotNull Project project,
                                                                      @NotNull ProblemDescriptor descriptor,
                                                                      @NotNull Sdk sdk) {
    if (!PythonSdkUtil.isRemote(sdk) && PySdkExtKt.adminPermissionsNeeded(sdk)) {
      final int answer = askToConfigureInterpreter(project, sdk);
      switch (answer) {
        case Messages.YES -> {
          new PyInterpreterInspection.ConfigureInterpreterFix().applyFix(project, descriptor);
          return true;
        }
        case Messages.CANCEL, -1 -> {
          return true;
        }
      }
    }
    return false;
  }

  private static int askToConfigureInterpreter(@NotNull Project project, @NotNull Sdk sdk) {
    final String sdkName = StringUtil.shortenTextWithEllipsis(sdk.getName(), 25, 0);
    final String text = PyPsiBundle.message("INSP.package.requirements.administrator.privileges.required.description", sdkName);
    final String[] options = {
      PyPsiBundle.message("INSP.package.requirements.administrator.privileges.required.button.configure"),
      PyPsiBundle.message("INSP.package.requirements.administrator.privileges.required.button.install.anyway"),
      CoreBundle.message("button.cancel")
    };
    return Messages.showIdeaMessageDialog(
      project,
      text,
      PyPsiBundle.message("INSP.package.requirements.administrator.privileges.required"),
      options,
      0,
      Messages.getWarningIcon(),
      null);
  }

  public static class PyInstallRequirementsFix implements LocalQuickFix {
    @NotNull private final @IntentionFamilyName String myName;
    @NotNull private final Module myModule;
    @NotNull private final Sdk mySdk;
    @NotNull private final List<PyRequirement> myUnsatisfied;
    @NotNull private final List<String> myExtraArgs;
    @Nullable private final PyPackageManagerUI.Listener myListener;

    public PyInstallRequirementsFix(@Nullable @IntentionFamilyName String name,
                                    @NotNull Module module, @NotNull Sdk sdk,
                                    @NotNull List<PyRequirement> unsatisfied) {
      this(name, module, sdk, unsatisfied, Collections.emptyList(), null);
    }

    public PyInstallRequirementsFix(@Nullable @IntentionFamilyName String name,
                                    @NotNull Module module,
                                    @NotNull Sdk sdk,
                                    @NotNull List<PyRequirement> unsatisfied,
                                    @NotNull List<String> extraArgs,
                                    @Nullable PyPackageManagerUI.Listener listener) {
      myName = name != null ? name : PyPsiBundle.message("QFIX.NAME.install.requirements", unsatisfied.size());
      myModule = module;
      mySdk = sdk;
      myUnsatisfied = unsatisfied;
      myExtraArgs = extraArgs;
      myListener = listener;
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
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      if (!checkAdminPermissionsAndConfigureInterpreter(project, descriptor, mySdk)) {
        PyUiUtil.clearFileLevelInspectionResults(project);
        installPackages(project);
      }
    }

    private void installPackages(@NotNull final Project project) {
      final PyPackageManager manager = PyPackageManager.getInstance(mySdk);
      final List<PyPackage> packages = manager.getPackages();
      if (packages == null) {
        return;
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
      boolean hasManagement;
      try {
        hasManagement = manager.hasManagement();
      }
      catch (ExecutionException e) {
        hasManagement = false;
      }
      if (!hasManagement) {
        final PyPackageManagerUI ui = new PyPackageManagerUI(project, mySdk, new RunningPackagingTasksListener(myModule) {
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
      final PyPackageManagerUI.Listener listener =
        myListener == null
        ? new RunningPackagingTasksListener(myModule)
        : new RunningPackagingTasksListener(myModule) {
          @Override
          public void started() {
            super.started();
            myListener.started();
          }

          @Override
          public void finished(List<ExecutionException> exceptions) {
            super.finished(exceptions);
            myListener.finished(exceptions);
          }
        };

      new PyPackageManagerUI(project, mySdk, listener).install(requirements, myExtraArgs);
    }
  }

  public static class InstallPackageQuickFix implements LocalQuickFix {
    public static final String CONFIRM_PACKAGE_INSTALLATION_PROPERTY = "python.confirm.package.installation";
    
    protected final @NotNull String myPackageName;

    public InstallPackageQuickFix(@NotNull String packageName) {
      myPackageName = packageName;
    }

    @Override
    public @NotNull String getFamilyName() {
      return PyBundle.message("python.unresolved.reference.inspection.install.package", myPackageName);
    }

    @Override
    public final void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      boolean isWellKnownPackage = ApplicationManager.getApplication()
        .getService(PyPIPackageRanking.class)
        .getPackageRank().containsKey(myPackageName);
      boolean confirmationEnabled = PropertiesComponent.getInstance().getBoolean(CONFIRM_PACKAGE_INSTALLATION_PROPERTY, true);
      if (!isWellKnownPackage && confirmationEnabled) {
        boolean confirmed = MessageDialogBuilder
          .yesNo(PyBundle.message("python.packaging.dialog.title.install.package.confirmation"),
                 PyBundle.message("python.packaging.dialog.message.install.package.confirmation", myPackageName))
          .icon(AllIcons.General.WarningDialog)
          .doNotAsk(new ConfirmPackageInstallationDoNotAskOption())
          .ask(project);
        if (!confirmed) {
          return;
        }
      }
      
      PsiElement element = descriptor.getPsiElement();
      if (element == null) return;
      Module module = ModuleUtilCore.findModuleForPsiElement(element);
      Sdk sdk = PythonSdkUtil.findPythonSdk(element);
      if (module != null && sdk != null) {
        new PyInstallRequirementsFix(
          getFamilyName(), module, sdk,
          Collections.singletonList(PyRequirementsKt.pyRequirement(myPackageName)),
          Collections.emptyList(),
          new RunningPackagingTasksListener(module) {
            @Override
            public void finished(List<ExecutionException> exceptions) {
              super.finished(exceptions);
              if (exceptions.isEmpty()) {
                onSuccess(descriptor);
              }
            }
          }
        ).applyFix(module.getProject(), descriptor);
      }
    }

    protected void onSuccess(@NotNull ProblemDescriptor descriptor) { }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public boolean availableInBatchMode() {
      return false;
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      return IntentionPreviewInfo.EMPTY;
    }

    private static class ConfirmPackageInstallationDoNotAskOption extends DoNotAskOption.Adapter {
      @Override
      public void rememberChoice(boolean isSelected, int exitCode) {
        if (isSelected && exitCode == Messages.OK) {
          PropertiesComponent.getInstance().setValue(CONFIRM_PACKAGE_INSTALLATION_PROPERTY, false, true);
        }
      }
    }
  }

  public static class InstallAndImportPackageQuickFix extends InstallPackageQuickFix {
    private final @Nullable String myAsName;

    public InstallAndImportPackageQuickFix(@NotNull String packageName, @Nullable String asName) {
      super(packageName);
      myAsName = asName;
    }

    @Override
    public @Nls @NotNull String getName() {
      return PyPsiBundle.message("QFIX.NAME.install.and.import.package", myPackageName);
    }

    @Override
    public @NotNull String getFamilyName() {
      return PyPsiBundle.message("QFIX.install.and.import.package");
    }

    @Override
    protected void onSuccess(@NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getPsiElement();
      if (element == null) return;
      WriteCommandAction.writeCommandAction(element.getProject())
        .withName(PyPsiBundle.message("INSP.package.requirements.add.import"))
        .withGroupId("Add import")
        .run(() -> {
          AddImportHelper.addImportStatement(element.getContainingFile(), myPackageName, myAsName,
                                             AddImportHelper.ImportPriority.THIRD_PARTY, element);
        });
    }
  }

  public static class PyGenerateRequirementsFileQuickFix implements LocalQuickFix {
    private final Module myModule;

    public PyGenerateRequirementsFileQuickFix(Module module) {
      myModule = module;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return PyPsiBundle.message("QFIX.add.imported.packages.to.requirements");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PyRequirementsTxtUtilKt.syncWithImports(myModule);
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }

  public static class RunningPackagingTasksListener implements PyPackageManagerUI.Listener {
    @NotNull private final Module myModule;

    public RunningPackagingTasksListener(@NotNull Module module) {
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


  private static final class IgnoreRequirementFix implements LocalQuickFix {

    @NotNull
    private final Set<String> myPackageNames;

    private IgnoreRequirementFix(@NotNull Set<String> packageNames) {
      myPackageNames = packageNames;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return PyPsiBundle.message("QFIX.NAME.ignore.requirements", myPackageNames.size());
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
          final Set<String> packagesToIgnore = new HashSet<>(myPackageNames);
          for (String pkg : inspection.ignoredPackages) {
            packagesToIgnore.remove(pkg);
          }

          if (!packagesToIgnore.isEmpty()) {
            inspection.ignoredPackages.addAll(packagesToIgnore);
            final ProjectInspectionProfileManager profileManager = ProjectInspectionProfileManager.getInstance(project);
            profileManager.fireProfileChanged();

            final Notification notification = BALLOON_NOTIFICATIONS
              .createNotification(
                packagesToIgnore.size() == 1
                ? PyPsiBundle.message("INSP.package.requirements.requirement.has.been.ignored", packagesToIgnore.iterator().next())
                : PyPsiBundle.message("INSP.package.requirements.requirements.have.been.ignored"),
                NotificationType.INFORMATION
              );

            notification.addAction(
              NotificationAction
                .createSimpleExpiring(
                  ActionsBundle.message("action.$Undo.text"),
                  () -> {
                    inspection.ignoredPackages.removeAll(packagesToIgnore);
                    profileManager.fireProfileChanged();
                  }
                )
            );

            notification.addAction(
              NotificationAction
                .createSimpleExpiring(
                  PyBundle.message("notification.action.edit.settings"),
                  () -> {
                    final InspectionProfileImpl profile = profileManager.getCurrentProfile();
                    final String toolName = PyPackageRequirementsInspection.class.getSimpleName();
                    EditInspectionToolsSettingsAction.editToolSettings(project, profile, toolName);
                  }
                )
            );

            notification.notify(project);
          }
        }
      }
    }
  }
}
