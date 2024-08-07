// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.openapi.options.ex.ConfigurableVisitor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.platform.backend.workspace.WorkspaceModelChangeListener;
import com.intellij.platform.workspace.jps.entities.ModuleEntity;
import com.intellij.platform.workspace.storage.EntityChange;
import com.intellij.platform.workspace.storage.VersionedStorageChange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.PathUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleEntityUtils;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonIdeLanguageCustomization;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.sdk.*;
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer;
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration;
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension;
import com.jetbrains.python.ui.PyUiUtil;
import kotlin.Pair;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PyInterpreterInspection extends PyInspection {

  private static final @NotNull Logger LOGGER = Logger.getInstance(PyInterpreterInspection.class);

  private static final @NotNull Pattern NAME = Pattern.compile("Python (?<version>\\d\\.\\d+)\\s*(\\((?<name>.+?)\\))?");

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                                 final boolean isOnTheFly,
                                                 final @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, PyInspectionVisitor.getContext(session));
  }

  public static class Visitor extends PyInspectionVisitor {
    /** Invalidated by {@link CacheCleaner}. */
    private static final AsyncLoadingCache<Module, List<PyDetectedSdk>> DETECTED_ASSOCIATED_ENVS_CACHE = Caffeine.newBuilder()
      .executor(AppExecutorUtil.getAppExecutorService())

      // Even though various listeners invalidate the cache on many actions, it's unfeasible to track for venv/conda interpreters
      // creation performed outside the IDE.
      // 20 seconds timeout is taken at random.
      .expireAfterWrite(Duration.ofSeconds(20))

      .weakKeys()
      .buildAsync(module -> {
        final List<Sdk> existingSdks = getExistingSdks();
        final UserDataHolderBase context = new UserDataHolderBase();
        return PySdkExtKt.detectAssociatedEnvironments(module, existingSdks, context);
      });

    public Visitor(@Nullable ProblemsHolder holder,
                   @NotNull TypeEvalContext context) {
      super(holder, context);
    }

    @Override
    public void visitPyFile(@NotNull PyFile node) {
      Module module = guessModule(node);
      if (module == null || isFileIgnored(node)) return;
      final Sdk sdk = PythonSdkUtil.findPythonSdk(module);

      final boolean pyCharm = PythonIdeLanguageCustomization.isMainlyPythonIde();

      final List<LocalQuickFix> fixes = new ArrayList<>();
      if (sdk == null) {
        final @InspectionMessage String message;
        if (pyCharm) {
          message = PyPsiBundle.message("INSP.interpreter.no.python.interpreter.configured.for.project");
        }
        else {
          message = PyPsiBundle.message("INSP.interpreter.no.python.interpreter.configured.for.module");
        }
        registerProblemWithCommonFixes(node, message, module, null, fixes, pyCharm);
      }
      else {
        final @NlsSafe String associatedModulePath = PySdkExtKt.getAssociatedModulePath(sdk);
        if (!PlatformUtils.isFleetBackend() && (associatedModulePath == null || PySdkExtKt.isAssociatedWithAnotherModule(sdk, module))) {
          final PyInterpreterInspectionQuickFixData fixData = PySdkProvider.EP_NAME.getExtensionList().stream()
            .map(ext -> ext.createEnvironmentAssociationFix(module, sdk, pyCharm, associatedModulePath))
            .filter(it -> it != null)
            .findFirst()
            .orElse(null);

          if (fixData != null) {
            fixes.add(fixData.getQuickFix());
            // noinspection HardCodedStringLiteral
            registerProblemWithCommonFixes(node, fixData.getMessage(), module, sdk, fixes, pyCharm);
            return;
          }
        }

        if (!PySdkExtKt.getSdkSeemsValid(sdk)) {
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

    private void registerProblemWithCommonFixes(PyFile node,
                                                @InspectionMessage String message,
                                                Module module,
                                                Sdk sdk,
                                                List<LocalQuickFix> fixes,
                                                boolean pyCharm) {
      if (pyCharm && sdk == null) {
        final String sdkName = ProjectRootManager.getInstance(node.getProject()).getProjectSdkName();
        ContainerUtil.addIfNotNull(fixes, getSuitableSdkFix(sdkName, module));
      }
      if (pyCharm) {
        fixes.add(new ConfigureInterpreterFix());
      }
      else {
        fixes.add(new InterpreterSettingsQuickFix(module));
      }

      registerProblem(node, message, fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
    }

    private static @Nullable LocalQuickFix getSuitableSdkFix(@Nullable String name, @NotNull Module module) {
      // this method is based on com.jetbrains.python.sdk.PySdkExtKt.suggestAssociatedSdkName
      // please keep it in sync with the mentioned method and com.jetbrains.python.PythonSdkConfigurator.configureSdk

      final List<Sdk> existingSdks = getExistingSdks();

      final var associatedSdk = PySdkExtKt.mostPreferred(PySdkExtKt.filterAssociatedSdks(module, existingSdks));
      if (associatedSdk != null) return new UseExistingInterpreterFix(associatedSdk, module);

      final UserDataHolderBase context = new UserDataHolderBase();

      List<PyDetectedSdk> detectedAssociatedEnvs = Collections.emptyList();
      while (true) {
        try {
          // Beware that this thread holds the read lock. Shouldn't wait too much.
          detectedAssociatedEnvs = DETECTED_ASSOCIATED_ENVS_CACHE.get(module).get(10, TimeUnit.MILLISECONDS);
          break;
        }
        catch (InterruptedException | TimeoutException ignored) {
          ProgressManager.checkCanceled();
        }
        catch (Exception e) {
          LOGGER.warn("Failed to get suitable sdk fix for name " + name + " and module " + module, e);
          break;
        }
      }

      final var detectedAssociatedSdk = ContainerUtil.getFirstItem(detectedAssociatedEnvs);
      if (detectedAssociatedSdk != null) {
        return new UseDetectedInterpreterFix(detectedAssociatedSdk, existingSdks, true, module);
      }

      final Pair<@IntentionName String, PyProjectSdkConfigurationExtension> textAndExtension =
        PyProjectSdkConfigurationExtension.findForModule(module);
      if (textAndExtension != null) {
        return new UseProvidedInterpreterFix(module, textAndExtension.getSecond(), textAndExtension.getFirst());
      }

      if (name != null) {
        final Matcher matcher = NAME.matcher(name);
        if (matcher.matches()) {
          final String venvName = matcher.group("name");
          if (venvName != null) {
            final PyDetectedSdk detectedAssociatedViaRootNameEnv = detectAssociatedViaRootNameEnv(venvName, module, existingSdks, context);
            if (detectedAssociatedViaRootNameEnv != null) {
              return new UseDetectedInterpreterFix(detectedAssociatedViaRootNameEnv, existingSdks, true, module);
            }
          }
          else {
            final PyDetectedSdk detectedSystemWideSdk = detectSystemWideSdk(matcher.group("version"), module, existingSdks, context);
            if (detectedSystemWideSdk != null) {
              return new UseDetectedInterpreterFix(detectedSystemWideSdk, existingSdks, false, module);
            }
          }
        }
      }

      if (PyCondaSdkCustomizer.Companion.getInstance().getSuggestSharedCondaEnvironments()) {
        final var sharedCondaEnv = PySdkExtKt.mostPreferred(PySdkExtKt.filterSharedCondaEnvs(module, existingSdks));
        if (sharedCondaEnv != null) {
          return new UseExistingInterpreterFix(sharedCondaEnv, module);
        }
      }

      final var systemWideSdk = PySdkExtKt.mostPreferred(PySdkExtKt.filterSystemWideSdks(existingSdks));
      if (systemWideSdk != null) {
        return new UseExistingInterpreterFix(systemWideSdk, module);
      }

      final var detectedSystemWideSdk = ContainerUtil.getFirstItem(PySdkExtKt.detectSystemWideSdks(module, existingSdks));
      if (detectedSystemWideSdk != null) {
        return new UseDetectedInterpreterFix(detectedSystemWideSdk, existingSdks, false, module);
      }

      return null;
    }

    private static @NotNull List<Sdk> getExistingSdks() {
      final ProjectSdksModel model = new ProjectSdksModel();
      model.reset(null);
      return ContainerUtil.filter(model.getSdks(), it -> it.getSdkType() instanceof PythonSdkType);
    }

    private static @Nullable PyDetectedSdk detectAssociatedViaRootNameEnv(@NotNull String associatedName,
                                                                          @NotNull Module module,
                                                                          @NotNull List<Sdk> existingSdks,
                                                                          @NotNull UserDataHolderBase context) {
      return findAssociatedViaRootNameEnv(
        associatedName,
        PySdkExtKt.detectVirtualEnvs(module, existingSdks, context),
        Visitor::getVirtualEnvRootName
      );
    }

    private static @Nullable PyDetectedSdk detectSystemWideSdk(@NotNull String version,
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

    private static @Nullable PyDetectedSdk findAssociatedViaRootNameEnv(@NotNull String associatedName,
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

    private static @Nullable String getVirtualEnvRootName(@NotNull PyDetectedSdk sdk) {
      final String path = sdk.getHomePath();
      return path == null ? null : getEnvRootName(PythonSdkUtil.getVirtualEnvRoot(path));
    }

    private static @Nullable String getEnvRootName(@Nullable File envRoot) {
      return envRoot == null ? null : PathUtil.getFileName(envRoot.getPath());
    }

    private static class CacheCleaner implements WorkspaceModelChangeListener, ProjectJdkTable.Listener {
      @Override
      public void jdkAdded(@NotNull Sdk jdk) {
        invalidate();
      }

      @Override
      public void jdkRemoved(@NotNull Sdk jdk) {
        invalidate();
      }

      @Override
      public void jdkNameChanged(@NotNull Sdk jdk, @NotNull String previousName) {
        invalidate();
      }

      /**
       * Invalidates the cache for the modules that were changed in any way. Especially interesting are
       * content roots changes and current Python interpreter changes.
       */
      @Override
      public void beforeChanged(@NotNull VersionedStorageChange event) {
        for (EntityChange<ModuleEntity> change : event.getChanges(ModuleEntity.class)) {
          ModuleEntity entity = change.getOldEntity();
          if (entity != null) {
            var module = ModuleEntityUtils.findModule(entity, event.getStorageBefore());
            if (module != null) {
              DETECTED_ASSOCIATED_ENVS_CACHE.synchronous().invalidate(module);
            }
          }
        }
      }

      private static void invalidate() {
        DETECTED_ASSOCIATED_ENVS_CACHE.synchronous().invalidateAll();
      }
    }
  }

  private static @Nullable Module guessModule(@NotNull PsiElement element) {
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

    private final @NotNull Module myModule;

    public InterpreterSettingsQuickFix(@NotNull Module module) {
      myModule = module;
    }

    @Override
    public @NotNull String getFamilyName() {
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

    public static void showPythonInterpreterSettings(@NotNull Project project, @Nullable Module module) {
      final var id = "com.jetbrains.python.configuration.PyActiveSdkModuleConfigurable";
      final var group = ConfigurableExtensionPointUtil.getConfigurableGroup(project, true);
      if (ConfigurableVisitor.findById(id, Collections.singletonList(group)) != null) {
        ShowSettingsUtilImpl.showSettingsDialog(project, id, null);
        return;
      }

      final ProjectSettingsService settingsService = ProjectSettingsService.getInstance(project);
      if (module == null || justOneModuleInheritingSdk(project, module)) {
        settingsService.openProjectSettings();
      }
      else {
        settingsService.openModuleSettings(module);
      }
    }

    private static boolean justOneModuleInheritingSdk(@NotNull Project project, @NotNull Module module) {
      return ProjectRootManager.getInstance(project).getProjectSdk() == null &&
             ModuleRootManager.getInstance(module).isSdkInherited() &&
             ModuleManager.getInstance(project).getModules().length < 2;
    }
  }

  public static final class ConfigureInterpreterFix implements LocalQuickFix {

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return PyPsiBundle.message("INSP.interpreter.configure.python.interpreter");
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }

    @Override
    public void applyFix(final @NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      if (element == null) return;

      final Module module = guessModule(element);
      if (module == null) return;

      PySdkPopupFactory.Companion.createAndShow(module);
    }
  }

  private static final class UseProvidedInterpreterFix implements LocalQuickFix {

    private final @NotNull Module myModule;

    private final @NotNull PyProjectSdkConfigurationExtension myExtension;

    private final @NotNull @IntentionName String myName;

    private UseProvidedInterpreterFix(@NotNull Module module,
                                      @NotNull PyProjectSdkConfigurationExtension extension,
                                      @NotNull @IntentionName String name) {
      myModule = module;
      myExtension = extension;
      myName = name;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return PyPsiBundle.message("INSP.interpreter.use.suggested.interpreter");
    }

    @Override
    public @IntentionName @NotNull String getName() {
      return myName;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PyProjectSdkConfiguration.INSTANCE.configureSdkUsingExtension(myModule, myExtension,
                                                                    () -> myExtension.createAndAddSdkForInspection(myModule));
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      // The quick fix doesn't change the code and is suggested on a file level
      return IntentionPreviewInfo.EMPTY;
    }
  }

  private abstract static class UseInterpreterFix<T extends Sdk> implements LocalQuickFix {

    protected final @NotNull T mySdk;

    protected UseInterpreterFix(@NotNull T sdk) {
      mySdk = sdk;
    }

    @Override
    public @IntentionFamilyName @NotNull String getFamilyName() {
      return PyPsiBundle.message("INSP.interpreter.use.suggested.interpreter");
    }

    @Override
    public @IntentionName @NotNull String getName() {
      return PyPsiBundle.message("INSP.interpreter.use.interpreter", PySdkPopupFactory.Companion.shortenNameInPopup(mySdk, 75));
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }

  private static final class UseExistingInterpreterFix extends UseInterpreterFix<Sdk> {

    private final @NotNull Module myModule;

    private UseExistingInterpreterFix(@NotNull Sdk existingSdk, @NotNull Module module) {
      super(existingSdk);
      myModule = module;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PyUiUtil.clearFileLevelInspectionResults(project);
      PyProjectSdkConfiguration.INSTANCE.setReadyToUseSdk(project, myModule, mySdk);
    }
  }

  private static final class UseDetectedInterpreterFix extends UseInterpreterFix<PyDetectedSdk> {

    private final @NotNull List<Sdk> myExistingSdks;

    private final boolean doAssociate;

    private final @NotNull Module myModule;

    private UseDetectedInterpreterFix(@NotNull PyDetectedSdk detectedSdk,
                                      @NotNull List<Sdk> existingSdks,
                                      boolean associate,
                                      @NotNull Module module) {
      super(detectedSdk);
      myExistingSdks = existingSdks;
      doAssociate = associate;
      myModule = module;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PyUiUtil.clearFileLevelInspectionResults(project);
      final Sdk newSdk = PySdkExtKt.setupAssociatedLogged(mySdk, myExistingSdks, BasePySdkExtKt.getBasePath(myModule), doAssociate);
      if (newSdk == null) {
        return;
      }

      SdkConfigurationUtil.addSdk(newSdk);
      PyProjectSdkConfiguration.INSTANCE.setReadyToUseSdk(project, myModule, newSdk);
    }
  }
}
