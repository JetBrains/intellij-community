// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.openapi.options.ex.ConfigurableVisitor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonIdeLanguageCustomization;
import com.jetbrains.python.configuration.PyActiveSdkModuleConfigurable;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.sdk.*;
import com.jetbrains.python.ui.PyUiUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PyInterpreterInspection extends PyInspection {
  @NotNull
  private static final Pattern NAME = Pattern.compile("Python (?<version>\\d\\.\\d+)\\s*(\\((?<name>.+?)\\))?");

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        final boolean isOnTheFly,
                                        @NotNull final LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {

    public Visitor(@Nullable ProblemsHolder holder,
                   @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyFile(@NotNull PyFile node) {
      Module module = guessModule(node);
      if (module == null || isFileIgnored(node)) return;
      final Sdk sdk = PythonSdkUtil.findPythonSdk(module);

      final boolean pyCharm = PythonIdeLanguageCustomization.isMainlyPythonIde();

      final List<LocalQuickFix> fixes = new ArrayList<>();
      if (sdk == null) {
        Optional<PyInterpreterInspectionQuickFixData> fixData = PySdkProvider.EP_NAME.extensions()
          .map(ext -> ext.createMissingSdkFix(module, node))
          .filter(it -> it != null)
          .findFirst();

        final @InspectionMessage String message;
        if (fixData.isPresent()) {
          fixes.add(fixData.get().getQuickFix());
          // noinspection HardCodedStringLiteral
          message = fixData.get().getMessage();
        }
        else if (pyCharm) {
          message = PyPsiBundle.message("INSP.interpreter.no.python.interpreter.configured.for.project");
        }
        else {
          message = PyPsiBundle.message("INSP.interpreter.no.python.interpreter.configured.for.module");
        }
        registerProblemWithCommonFixes(node, message, module, null, fixes, pyCharm);
      }
      else {
        final @NlsSafe String associatedModulePath = PySdkExtKt.getAssociatedModulePath(sdk);
        if (associatedModulePath == null || PySdkExtKt.isAssociatedWithAnotherModule(sdk, module)) {
          PySdkProvider.EP_NAME.extensions()
            .map(ext -> ext.createEnvironmentAssociationFix(module, sdk, pyCharm, associatedModulePath))
            .filter(it -> it != null)
            .findFirst().ifPresent(fixData ->  {
              fixes.add(fixData.getQuickFix());
              // noinspection HardCodedStringLiteral
              registerProblemWithCommonFixes(node, fixData.getMessage(), module, sdk, fixes, pyCharm);
          });
        }
        else if (PythonSdkUtil.isInvalid(sdk)) {
          final @InspectionMessage String message;
          if (pyCharm) {
            message = PyPsiBundle.message("INSP.interpreter.invalid.python.interpreter.selected.for.project");
          }
          else {
            message = PyPsiBundle.message("INSP.interpreter.invalid.python.interpreter.selected.for.module");
          }
          registerProblemWithCommonFixes(node, message, module, sdk, fixes, pyCharm);
        }
        else {
          final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(sdk);
          if (!LanguageLevel.SUPPORTED_LEVELS.contains(languageLevel)) {
            final @InspectionMessage String message;
            if (pyCharm) {
              message = PyPsiBundle.message("INSP.interpreter.python.has.reached.its.end.of.life.and.is.no.longer.supported.in.pycharm",
                                         languageLevel);
            }
            else {
              message = PyPsiBundle.message("INSP.interpreter.python.has.reached.its.end.life.and.is.no.longer.supported.in.python.plugin",
                                         languageLevel);
            }
            registerProblemWithCommonFixes(node, message, module, sdk, fixes, pyCharm);
          }
        }
      }
    }

    private void registerProblemWithCommonFixes(PyFile node, @InspectionMessage String message, Module module, Sdk sdk, List<LocalQuickFix> fixes, boolean pyCharm) {
      if (pyCharm && sdk == null) {
        final String sdkName = ProjectRootManager.getInstance(node.getProject()).getProjectSdkName();
        if (sdkName != null) {
          ContainerUtil.addIfNotNull(fixes, getSuitableSdkFix(sdkName, module));
        }
      }
      if (pyCharm) {
        fixes.add(new ConfigureInterpreterFix());
      }
      else {
        fixes.add(new InterpreterSettingsQuickFix(module));
      }

      registerProblem(node, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    }

    @Nullable
    private static LocalQuickFix getSuitableSdkFix(@NotNull String name, @NotNull Module module) {
      // this method is based on com.jetbrains.python.sdk.PySdkExtKt.suggestAssociatedSdkName

      final List<Sdk> existingSdks = getExistingSdks();

      final Sdk associatedSdk = PySdkExtKt.findExistingAssociatedSdk(module, existingSdks);
      if (associatedSdk != null) return new UseExistingInterpreterFix(associatedSdk, module);

      final UserDataHolderBase context = new UserDataHolderBase();

      final PyDetectedSdk detectedAssociatedSdk = StreamEx.of(PySdkExtKt.detectVirtualEnvs(module, existingSdks, context))
        .findFirst(sdk -> PySdkExtKt.isAssociatedWithModule(sdk, module))
        .orElse(null);

      if (detectedAssociatedSdk != null) return new UseDetectedInterpreterFix(detectedAssociatedSdk, existingSdks, true, module);

      final Matcher matcher = NAME.matcher(name);
      if (!matcher.matches()) return null;

      final String venvName = matcher.group("name");
      if (venvName != null) {
        final PyDetectedSdk detectedAssociatedViaRootNameEnv = detectAssociatedViaRootNameEnv(venvName, module, existingSdks, context);
        if (detectedAssociatedViaRootNameEnv != null) {
          return new UseDetectedInterpreterFix(detectedAssociatedViaRootNameEnv, existingSdks, true, module);
        }
      }
      else {
        final PyDetectedSdk detectedSystemWideSdk = detectSystemWideSdk(matcher.group("version"), module, existingSdks, context);
        if (detectedSystemWideSdk != null) return new UseDetectedInterpreterFix(detectedSystemWideSdk, existingSdks, false, module);
      }

      return null;
    }

    @NotNull
    private static List<Sdk> getExistingSdks() {
      final ProjectSdksModel model = new ProjectSdksModel();
      model.reset(null);
      return ContainerUtil.filter(model.getSdks(), it -> it.getSdkType() instanceof PythonSdkType);
    }

    @Nullable
    private static PyDetectedSdk detectAssociatedViaRootNameEnv(@NotNull String associatedName,
                                                                @NotNull Module module,
                                                                @NotNull List<Sdk> existingSdks,
                                                                @NotNull UserDataHolderBase context) {
      return findAssociatedViaRootNameEnv(
        associatedName,
        PySdkExtKt.detectVirtualEnvs(module, existingSdks, context),
        Visitor::getVirtualEnvRootName
      );
    }

    @Nullable
    private static PyDetectedSdk detectSystemWideSdk(@NotNull String version,
                                                     @NotNull Module module,
                                                     @NotNull List<Sdk> existingSdks,
                                                     @NotNull UserDataHolderBase context) {
      final LanguageLevel parsedVersion = LanguageLevel.fromPythonVersion(version);

      if (parsedVersion.toString().equals(version)) {
        return ContainerUtil.find(
          PySdkExtKt.detectSystemWideSdks(module, existingSdks, context),
          sdk -> PySdkExtKt.getGuessedLanguageLevel(sdk) == parsedVersion
        );
      }

      return null;
    }

    @Nullable
    private static PyDetectedSdk findAssociatedViaRootNameEnv(@NotNull String associatedName,
                                                              @NotNull List<PyDetectedSdk> envs,
                                                              @NotNull Function<PyDetectedSdk, String> envRootName) {
      return StreamEx
        .of(envs)
        .filter(sdk -> associatedName.equals(envRootName.apply(sdk)))
        .max(
          Comparator
            .comparing(PySdkExtKt::getGuessedLanguageLevel)
            .thenComparing(PyDetectedSdk::getHomePath)
        )
        .orElse(null);
    }

    @Nullable
    private static String getVirtualEnvRootName(@NotNull PyDetectedSdk sdk) {
      final String path = sdk.getHomePath();
      return path == null ? null : getEnvRootName(PythonSdkUtil.getVirtualEnvRoot(path));
    }

    @Nullable
    private static String getEnvRootName(@Nullable File envRoot) {
      return envRoot == null ? null : PathUtil.getFileName(envRoot.getPath());
    }
  }

  @Nullable
  private static Module guessModule(@NotNull PsiElement element) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    if (module == null) {
      Module[] modules = ModuleManager.getInstance(element.getProject()).getModules();
      if (modules.length != 1) {
        return null;
      }
      module = modules[0];
    }
    return module;
  }

  private static boolean isFileIgnored(@NotNull PyFile pyFile) {
    return PyInspectionExtension.EP_NAME.getExtensionList().stream().anyMatch(ep -> ep.ignoreInterpreterWarnings(pyFile));
  }

  public static final class InterpreterSettingsQuickFix implements LocalQuickFix {

    @NotNull
    private final Module myModule;

    public InterpreterSettingsQuickFix(@NotNull Module module) {
      myModule = module;
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return PlatformUtils.isPyCharm()
             ? PyPsiBundle.message("INSP.interpreter.interpreter.settings")
             : PyPsiBundle.message("INSP.interpreter.configure.python.interpreter");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      showPythonInterpreterSettings(project, myModule);
    }

    public static void showPythonInterpreterSettings(@NotNull Project project, @NotNull Module module) {
      if (hasPythonSdkConfigurable(project)) {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, PyActiveSdkModuleConfigurable.class);
        return;
      }

      final ProjectSettingsService settingsService = ProjectSettingsService.getInstance(project);
      if (justOneModuleInheritingSdk(project, module)) {
        settingsService.openProjectSettings();
      }
      else {
        settingsService.openModuleSettings(module);
      }
    }

    private static boolean hasPythonSdkConfigurable(@NotNull Project project) {
      if (PlatformUtils.isPyCharm()) return true;

      final List<ConfigurableGroup> groups = Collections.singletonList(ConfigurableExtensionPointUtil.getConfigurableGroup(project, true));
      return ConfigurableVisitor.findByType(PyActiveSdkModuleConfigurable.class, groups) != null;
    }

    private static boolean justOneModuleInheritingSdk(@NotNull Project project, @NotNull Module module) {
      return ProjectRootManager.getInstance(project).getProjectSdk() == null &&
             ModuleRootManager.getInstance(module).isSdkInherited() &&
             ModuleManager.getInstance(project).getModules().length < 2;
    }
  }

  public static final class ConfigureInterpreterFix implements LocalQuickFix {
    @NotNull
    @Override
    public String getFamilyName() {
      return PyPsiBundle.message("INSP.interpreter.configure.python.interpreter");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element == null) return;

      final Module module = guessModule(element);
      if (module == null) return;

      PySdkPopupFactory.Companion.createAndShow(project, module);
    }
  }

  private static abstract class UseInterpreterFix<T extends Sdk> implements LocalQuickFix {

    @NotNull
    protected final T mySdk;

    protected UseInterpreterFix(@NotNull T sdk) {
      mySdk = sdk;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @Override
    public @NotNull String getFamilyName() {
      return PyPsiBundle.message("INSP.interpreter.use.suggested.interpreter");
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @Override
    public @NotNull String getName() {
      return PyPsiBundle.message("INSP.interpreter.use.interpreter", PySdkPopupFactory.Companion.shortenNameInPopup(mySdk, 75));
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }

  private static final class UseExistingInterpreterFix extends UseInterpreterFix<Sdk> {

    @NotNull
    private final Module myModule;

    private UseExistingInterpreterFix(@NotNull Sdk existingSdk, @NotNull Module module) {
      super(existingSdk);
      myModule = module;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PyUiUtil.clearFileLevelInspectionResults(project);
      SdkConfigurationUtil.setDirectoryProjectSdk(project, mySdk);
      PySdkExtKt.excludeInnerVirtualEnv(myModule, mySdk);
    }
  }

  private static final class UseDetectedInterpreterFix extends UseInterpreterFix<PyDetectedSdk> {

    @NotNull
    private final List<Sdk> myExistingSdks;

    private final boolean myAssociate;

    @NotNull
    private final Module myModule;

    private UseDetectedInterpreterFix(@NotNull PyDetectedSdk detectedSdk,
                                      @NotNull List<Sdk> existingSdks,
                                      boolean associate,
                                      @NotNull Module module) {
      super(detectedSdk);
      myExistingSdks = existingSdks;
      myAssociate = associate;
      myModule = module;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PyUiUtil.clearFileLevelInspectionResults(project);
      final Sdk newSdk = myAssociate
                         ? PySdkExtKt.setupAssociated(mySdk, myExistingSdks, BasePySdkExtKt.getBasePath(myModule))
                         : PySdkExtKt.setup(mySdk, myExistingSdks);
      if (newSdk == null) return;

      SdkConfigurationUtil.addSdk(newSdk);
      if (myAssociate) PySdkExtKt.associateWithModule(newSdk, myModule, null);
      SdkConfigurationUtil.setDirectoryProjectSdk(project, newSdk);
      if (myAssociate) PySdkExtKt.excludeInnerVirtualEnv(myModule, newSdk);
    }
  }
}
