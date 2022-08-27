// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.EditorNotificationProvider;
import com.intellij.util.PathUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.workspaceModel.ide.WorkspaceModelChangeListener;
import com.intellij.workspaceModel.ide.impl.legacyBridge.module.ModuleEntityUtils;
import com.intellij.workspaceModel.storage.EntityChange;
import com.intellij.workspaceModel.storage.VersionedStorageChange;
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.PythonIdeLanguageCustomization;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.inspections.quickfix.sdk.*;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer;
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension;
import kotlin.Pair;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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

import static com.jetbrains.python.PythonHelper.guessModule;

public final class PyEditorNotificationProvider implements DumbAware, EditorNotificationProvider {

  @NotNull
  private static final Logger LOGGER = Logger.getInstance(PyEditorNotificationProvider.class);

  @NotNull
  private static final Pattern NAME = Pattern.compile("Python (?<version>\\d\\.\\d+)\\s*(\\((?<name>.+?)\\))?");
  /**
   * Invalidated by {@link CacheCleaner}.
   */
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

  private static PyFile getPyFile(@NotNull Project project,
                    @NotNull VirtualFile file) {
    PsiFile psiFile = PsiManager.getInstance(project).findFile(file);
    if (psiFile == null) {
      return null;
    }

    if (!(psiFile instanceof PyFile)) {
      psiFile = ContainerUtil.find(psiFile.getViewProvider().getAllFiles(), it -> it instanceof PyFile);
    }
    return (PyFile)psiFile;
  }

  @Override
  public @Nullable Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> collectNotificationData(@NotNull Project project,
                                                                                                                @NotNull VirtualFile file) {
    PyFile node = getPyFile(project, file);
    if (node == null) {
      return null;
    }
    Module module = guessModule(node);
    if (module == null || isFileIgnored(node)) return null;
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
      return registerProblemWithCommonFixes(node, message, module, null, fixes, pyCharm);
    }
    else {
      final @NlsSafe String associatedModulePath = PySdkExtKt.getAssociatedModulePath(sdk);
      if (associatedModulePath == null || PySdkExtKt.isAssociatedWithAnotherModule(sdk, module)) {
        final PyInterpreterInspectionQuickFixData fixData = PySdkProvider.EP_NAME.getExtensionList().stream()
          .map(ext -> ext.createEnvironmentAssociationFix(module, sdk, pyCharm, associatedModulePath))
          .filter(it -> it != null)
          .findFirst()
          .orElse(null);

        if (fixData != null) {
          fixes.add(fixData.getQuickFix());
          // noinspection HardCodedStringLiteral
          return registerProblemWithCommonFixes(node, fixData.getMessage(), module, sdk, fixes, pyCharm);
        }
      }

      if (PythonSdkUtil.isInvalid(sdk)) {
        final @InspectionMessage String message;
        if (pyCharm) {
          message = PyPsiBundle.message("INSP.interpreter.invalid.python.interpreter.selected.for.project");
        }
        else {
          message = PyPsiBundle.message("INSP.interpreter.invalid.python.interpreter.selected.for.module");
        }
        return registerProblemWithCommonFixes(node, message, module, sdk, fixes, pyCharm);
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
          return registerProblemWithCommonFixes(node, message, module, sdk, fixes, pyCharm);
        }
      }
    }
    return null;
  }

  private static Function<? super @NotNull FileEditor, ? extends @Nullable JComponent> registerProblemWithCommonFixes(PyFile node,
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

    return (Function<FileEditor, JComponent>)editor -> {
      EditorNotificationPanel result = new EditorNotificationPanel(editor, EditorNotificationPanel.Status.Warning);
      result.setText(message);
      FileProblemDescriptor descriptor = new FileProblemDescriptor(node.getContainingFile());
      for (LocalQuickFix fix : fixes) {
        result.createActionLabel(fix.getName(), () -> fix.applyFix(node.getProject(), descriptor));
      }
      return result;
    };
  }

  private static class FileProblemDescriptor extends ProblemDescriptorBase {
    private FileProblemDescriptor(@NotNull PsiFile file) {
      super(file, file, "", null, ProblemHighlightType.INFORMATION, false, null, false, false);
    }
  }

  @Nullable
  private static LocalQuickFix getSuitableSdkFix(@Nullable String name, @NotNull Module module) {
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
    if (detectedAssociatedSdk != null) return new UseDetectedInterpreterFix(detectedAssociatedSdk, existingSdks, true, module);

    final Pair<@IntentionName String, PyProjectSdkConfigurationExtension> textAndExtension
      = PyProjectSdkConfigurationExtension.findForModule(module);
    if (textAndExtension != null) return new UseProvidedInterpreterFix(module, textAndExtension.getSecond(), textAndExtension.getFirst());

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
          if (detectedSystemWideSdk != null) return new UseDetectedInterpreterFix(detectedSystemWideSdk, existingSdks, false, module);
        }
      }
    }

    if (PyCondaSdkCustomizer.Companion.getInstance().getSuggestSharedCondaEnvironments()) {
      final var sharedCondaEnv = PySdkExtKt.mostPreferred(PySdkExtKt.filterSharedCondaEnvs(module, existingSdks));
      if (sharedCondaEnv != null) return new UseExistingInterpreterFix(sharedCondaEnv, module);

      final var detectedCondaEnv = ContainerUtil.getFirstItem(PySdkExtKt.detectCondaEnvs(module, existingSdks, context));
      if (detectedCondaEnv != null) return new UseDetectedInterpreterFix(detectedCondaEnv, existingSdks, false, module);
    }

    final var systemWideSdk = PySdkExtKt.mostPreferred(PySdkExtKt.filterSystemWideSdks(existingSdks));
    if (systemWideSdk != null) return new UseExistingInterpreterFix(systemWideSdk, module);

    final var detectedSystemWideSdk = ContainerUtil.getFirstItem(PySdkExtKt.detectSystemWideSdks(module, existingSdks));
    if (detectedSystemWideSdk != null) return new UseDetectedInterpreterFix(detectedSystemWideSdk, existingSdks, false, module);

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
      PyEditorNotificationProvider::getVirtualEnvRootName
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

  public static class CacheCleaner implements WorkspaceModelChangeListener, ProjectJdkTable.Listener {
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

  private static boolean isFileIgnored(@NotNull PyFile pyFile) {
    return PyInspectionExtension.EP_NAME.getExtensionList().stream().anyMatch(ep -> ep.ignoreInterpreterWarnings(pyFile));
  }
}
