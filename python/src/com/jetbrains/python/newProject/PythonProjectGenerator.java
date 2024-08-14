// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.newProject;

import com.intellij.execution.ExecutionException;
import com.intellij.facet.ui.ValidationResult;
import com.intellij.icons.AllIcons.General;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.DirectoryProjectGeneratorBase;
import com.intellij.util.BooleanFunction;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyPsiPackageUtil;
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo;
import com.jetbrains.python.newProject.collector.PythonNewProjectWizardCollector;
import com.jetbrains.python.packaging.PyPackage;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.PyPackageUtil;
import com.jetbrains.python.packaging.PyPackagesNotificationPanel;
import com.jetbrains.python.packaging.ui.PyPackageManagementService;
import com.jetbrains.python.remote.*;
import com.jetbrains.python.sdk.PyLazySdk;
import com.jetbrains.python.sdk.PythonSdkUtil;
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMode;
import com.jetbrains.python.statistics.PyStatisticToolsKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Consumer;


/**
 * This class encapsulates remote settings, so one should extend it for any python project that supports remote generation, at least
 * Instead of {@link #generateProject(Project, VirtualFile, PyNewProjectSettings, Module)} inheritor shall use
 * {@link #configureProject(Project, VirtualFile, PyNewProjectSettings, Module, PyProjectSynchronizer)}*
 * or {@link #configureProjectNoSettings(Project, VirtualFile, Module)} (see difference below)
 * <br/>
 * If your project does not support remote projects generation, be sure to set flag in ctor:{@link #PythonProjectGenerator(boolean)}
 * <br/>
 * <h2>Module vs PyCharm projects</h2>
 * <p>
 * When you create project in PyCharm it always calls {@link #configureProject(Project, VirtualFile, PyNewProjectSettings, Module, PyProjectSynchronizer)},
 * but in Intellij Plugin settings are not ready to the moment of project creation, so there are 2 ways to support plugin:
 *   <ol>
 *     <li>Do not lean on settings at all. You simply implement {@link #configureProjectNoSettings(Project, VirtualFile, Module)}
 *     This way is common for project templates.
 *    </li>
 *    <li>Implement framework as facet. {@link #configureProject(Project, VirtualFile, PyNewProjectSettings, Module, PyProjectSynchronizer)}
 *     will never be called in this case, so you can use "onFacetCreated" event of facet provider</li>
 *   </li>
 *   </ol>
 * </p>
 * <h2>How to report framework installation failures</h2>
 * <p>{@link PyNewProjectSettings#getSdk()} may return null, or something else may prevent package installation.
 * Use {@link #reportPackageInstallationFailure(String, Pair)} in this case.
 * </p>
 *
 * @param <T> project settings
 */
public abstract class PythonProjectGenerator<T extends PyNewProjectSettings> extends DirectoryProjectGeneratorBase<T> {
  public static final PyNewProjectSettings NO_SETTINGS = new PyNewProjectSettings();
  private static final Logger LOGGER = Logger.getInstance(PythonProjectGenerator.class);

  private final List<SettingsListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private final boolean myAllowRemoteProjectCreation;
  private @Nullable MouseListener myErrorLabelMouseListener;

  protected Consumer<String> myErrorCallback;

  @Nullable
  private final PythonInterpreterSelectionMode preferredEnvironmentType;

  /**
   * @param allowRemoteProjectCreation if project of this type could be created remotely
   */
  protected PythonProjectGenerator(final boolean allowRemoteProjectCreation) {
    this(allowRemoteProjectCreation, null);
  }

  protected PythonProjectGenerator() {
    this(false, null);
  }

  /**
   * @param allowRemoteProjectCreation if project of this type could be created remotely
   * @param preferredInterpreter       interpreter type to select by default
   */
  protected PythonProjectGenerator(final boolean allowRemoteProjectCreation,
                                   @Nullable PythonInterpreterSelectionMode preferredInterpreter) {
    myAllowRemoteProjectCreation = allowRemoteProjectCreation;
    preferredEnvironmentType = preferredInterpreter;
  }

  public final void setErrorCallback(final @NotNull Consumer<String> errorCallback) {
    myErrorCallback = errorCallback;
  }

  public @Nullable JComponent getSettingsPanel(File baseDir) throws ProcessCanceledException {
    return null;
  }

  /**
   * Upper part of project generation wizard panel could be customized
   */
  @ApiStatus.Internal
  @Nullable
  public MainPartUiCustomizer getMainPartUiCustomizer() {
    return null;
  }

  public @Nullable JPanel extendBasePanel() throws ProcessCanceledException {
    return null;
  }

  /**
   * Checks if project type and remote ask allows project creation.
   * Throws exception with reason if can't
   *
   * @param sdk              sdk to check
   * @param projectDirectory base project directory
   * @throws PyNoProjectAllowedOnSdkException project can't be created (check message)
   */
  public void checkProjectCanBeCreatedOnSdk(final @NotNull Sdk sdk,
                                            final @NotNull File projectDirectory) throws PyNoProjectAllowedOnSdkException {

    // Check if project does not support remote creation at all
    if (!myAllowRemoteProjectCreation && PythonSdkUtil.isRemote(sdk)) {
      throw new PyNoProjectAllowedOnSdkException(
        PyBundle.message("python.remote.interpreter.can.t.create.project.this.type"));
    }


    // Check if project synchronizer could be used with this project dir
    // No project can be created remotely if project synchronizer can't work with it

    final PyProjectSynchronizer synchronizer = PyProjectSynchronizerProvider.getSynchronizer(sdk);
    if (synchronizer == null) {
      return;
    }
    final String syncError = synchronizer.checkSynchronizationAvailable(new PySyncCheckOnly(projectDirectory));
    if (syncError != null) {
      throw new PyNoProjectAllowedOnSdkException(syncError);
    }
  }

  @Override
  public final void generateProject(final @NotNull Project project,
                                    final @NotNull VirtualFile baseDir,
                                    final @NotNull T settings,
                                    final @NotNull Module module) {
    // Use NO_SETTINGS to avoid nullable settings of project generator
    if (settings == NO_SETTINGS) {
      // We are in Intellij Module and framework is implemented as project template, not facet.
      // See class doc for mote info
      configureProjectNoSettings(project, baseDir, module);
      return;
    }

    /*Instead of this method overwrite ``configureProject``*/

    // If we deal with remote project -- use remote manager to configure it
    final Sdk sdk = settings.getSdk();
    LOGGER.info(String.format("Wizard created PythonSDK %s", sdk));
    if (sdk instanceof PyLazySdk) {
      final Sdk createdSdk = ((PyLazySdk)sdk).create();
      LOGGER.info(String.format("Lazy PythonSDK generated sdk: %s", createdSdk));
      settings.setSdk(createdSdk);
    }

    final PyProjectSynchronizer synchronizer = sdk != null ? PyProjectSynchronizerProvider.getSynchronizer(sdk) : null;

    if (synchronizer != null) {
      // Before project creation we need to configure sync
      // We call "checkSynchronizationAvailable" until it returns success (means sync is available)
      // Or user confirms she does not need sync
      String userProvidedPath = settings.getRemotePath();
      while (true) {
        final String syncError = synchronizer.checkSynchronizationAvailable(new PySyncCheckCreateIfPossible(module, userProvidedPath));
        if (syncError == null) {
          break;
        }
        userProvidedPath = null; // According to checkSynchronizationAvailable should be cleared
        final String message =
          PyBundle.message("python.new.project.synchronization.not.configured.dialog.message", syncError);
        if (Messages.showYesNoDialog(project,
                                     message,
                                     PyBundle.message("python.new.project.synchronization.not.configured.dialog.title"),
                                     General.WarningDialog) == Messages.YES) {
          break;
        }
      }
    }

    configureProject(project, baseDir, settings, module, synchronizer);
    reportStatistics(settings);
  }

  /**
   * Report FUS statics on the created project.
   * Inheritors can override this method to supply customized data for the statistics.
   */
  protected void reportStatistics(final @NotNull T settings) {
    var statisticsInfo = settings.getInterpreterInfoForStatistics();
    if (statisticsInfo instanceof InterpreterStatisticsInfo interpreterStatisticsInfo && settings.getSdk() != null) {
      PythonNewProjectWizardCollector.logPythonNewProjectGenerated(interpreterStatisticsInfo,
                                                                   PyStatisticToolsKt.getVersion(settings.getSdk()),
                                                                   this.getClass(),
                                                                   Collections.emptyList());
    }
  }

  /**
   * Same as {@link #configureProject(Project, VirtualFile, PyNewProjectSettings, Module, PyProjectSynchronizer)}
   * but with out of settings. Called by Intellij Plugin when framework is installed as project template.
   */
  protected void configureProjectNoSettings(final @NotNull Project project,
                                            final @NotNull VirtualFile baseDir,
                                            final @NotNull Module module) {
    throw new IllegalStateException(String.format("%s does not support project creation with out of settings. " +
                                                  "See %s doc for detail", getClass(), PythonProjectGenerator.class));
  }

  /**
   * Does real work to generate project.
   * Parent class does its best to handle remote interpreters.
   * Inheritors should only create project.
   * To support remote project creation, be sure to use {@link PyProjectSynchronizer}.
   * <br/>
   * When overwriting this method, <strong>be sure</strong> to call super() or call
   * {@link PyProjectSynchronizer#syncProject(Module, PySyncDirection, Consumer, String...)}  at least once: automatic sync works only after it.
   *
   * @param synchronizer null if project is local and no sync required.
   *                     Otherwise, be sure to use it move code between local (java) and remote (python) side.
   *                     Remote interpreters can't be used with out of it. Contract is following:
   *                     <ol>
   *                     <li>Create some code on python (remote) side using helpers</li>
   *                     <li>call {@link PyProjectSynchronizer#syncProject(Module, PySyncDirection, Consumer, String...)}</li>
   *                     <li>Change locally</li>
   *                     <li>call {@link PyProjectSynchronizer#syncProject(Module, PySyncDirection, Consumer, String...)} again in opposite direction</li>
   *                     </ol>
   */

  protected void configureProject(final @NotNull Project project,
                                  final @NotNull VirtualFile baseDir,
                                  final @NotNull T settings,
                                  final @NotNull Module module,
                                  final @Nullable PyProjectSynchronizer synchronizer) {
    // Automatic deployment works only after first sync
    if (synchronizer != null) {
      synchronizer.syncProject(module, PySyncDirection.LOCAL_TO_REMOTE, null);
    }
  }

  public Object getProjectSettings() {
    return new PyNewProjectSettings();
  }

  public ValidationResult warningValidation(final @Nullable Sdk sdk) {
    return ValidationResult.OK;
  }

  public void addSettingsStateListener(@NotNull SettingsListener listener) {
    myListeners.add(listener);
  }

  public void locationChanged(final @NotNull String newLocation) {
  }

  /**
   * @return Python interpreter type this generator prefered to use. Null if no preferences
   */
  public final @Nullable PythonInterpreterSelectionMode getPreferredEnvironmentType() {
    return preferredEnvironmentType;
  }

  public interface SettingsListener {
    void stateChanged();
  }

  public void fireStateChanged() {
    for (SettingsListener listener : myListeners) {
      listener.stateChanged();
    }
  }

  public void afterProjectGenerated(final @NotNull Project project) {
  }

  public void addErrorLabelMouseListener(final @NotNull MouseListener mouseListener) {
    myErrorLabelMouseListener = mouseListener;
  }

  public @Nullable MouseListener getErrorLabelMouseListener() {
    return myErrorLabelMouseListener;
  }

  public void createAndAddVirtualEnv(Project project, PyNewProjectSettings settings) {
  }

  /**
   * @param sdkAndException if you have SDK and execution exception provide them here (both must not be null).
   */
  protected static void reportPackageInstallationFailure(final @NotNull String frameworkName,
                                                         final @Nullable Pair<Sdk, ExecutionException> sdkAndException) {

    final PyPackageManagementService.PyPackageInstallationErrorDescription errorDescription =
      getErrorDescription(sdkAndException, frameworkName);
    final Application app = ApplicationManager.getApplication();
    app.invokeLater(() -> {
      PyPackagesNotificationPanel.showPackageInstallationError(PyBundle.message("python.new.project.install.failed.title", frameworkName),
                                                               errorDescription);
    });
  }

  private static @NotNull PyPackageManagementService.PyPackageInstallationErrorDescription getErrorDescription(final @Nullable Pair<Sdk, ExecutionException> sdkAndException,
                                                                                                               @NotNull String packageName) {
    PyPackageManagementService.PyPackageInstallationErrorDescription errorDescription = null;
    if (sdkAndException != null) {
      final ExecutionException exception = sdkAndException.second;
      errorDescription =
        PyPackageManagementService.toErrorDescription(Collections.singletonList(exception), sdkAndException.first, packageName);
      if (errorDescription == null) {
        errorDescription = PyPackageManagementService.PyPackageInstallationErrorDescription.createFromMessage(exception.getMessage());
      }
    }

    if (errorDescription == null) {
      errorDescription = PyPackageManagementService.PyPackageInstallationErrorDescription.createFromMessage(
        PyBundle.message("python.new.project.error.solution.another.sdk"));
    }
    return errorDescription;
  }


  //TODO: Support for plugin also

  /**
   * Installs framework and runs callback on success.
   * Installation runs in modal dialog and callback is posted to AWT thread.
   * <p>
   * If "forceInstallFramework" is passed then installs framework in any case.
   * If SDK is remote then checks if it has interpreter and installs if missing
   *
   * @param frameworkName         user-readable framework name (i.e. "Django")
   * @param requirement           name of requirement to install (i.e. "django")
   * @param forceInstallFramework pass true if you are sure required framework is missing
   * @param callback              to be called after installation (or instead of is framework is installed) on AWT thread
   * @return future to be used instead of callback.
   */
  public static @NotNull Future<Void> installFrameworkIfNeeded(final @NotNull Project project,
                                                               final @NotNull String frameworkName,
                                                               final @NotNull String requirement,
                                                               final @Nullable Sdk sdk,
                                                               final boolean forceInstallFramework,
                                                               final @Nullable Runnable callback) {
    return installFrameworkIfNeeded(project, frameworkName, requirement, sdk, forceInstallFramework, false, callback);
  }

  public static @NotNull Future<Void> installFrameworkInBackground(final @NotNull Project project,
                                                                   final @NotNull String frameworkName,
                                                                   final @NotNull String requirement,
                                                                   final @Nullable Sdk sdk,
                                                                   final boolean forceInstallFramework,
                                                                   final @Nullable Runnable callback) {
    return installFrameworkIfNeeded(project, frameworkName, requirement, sdk, forceInstallFramework, true, callback);
  }

  private static @NotNull Future<Void> installFrameworkIfNeeded(final @NotNull Project project,
                                                                final @NotNull String frameworkName,
                                                                final @NotNull String requirement,
                                                                final @Nullable Sdk sdk,
                                                                final boolean forceInstallFramework,
                                                                boolean asBackgroundTask,
                                                                final @Nullable Runnable callback) {

    var future = new CompletableFuture<Void>();
    if (sdk == null) {
      reportPackageInstallationFailure(frameworkName, null);
      future.completeExceptionally(new RuntimeException(("No SDK provided")));
      return future;
    }

    // For remote SDK we are not sure if framework exists or not, so we'll check it anyway
    if (forceInstallFramework || PythonSdkUtil.isRemote(sdk)) {

      if (asBackgroundTask) {
        ProgressManager.getInstance()
          .run(new Task.Backgroundable(project, PyBundle.message("python.install.framework.ensure.installed", frameworkName), false) {
            @Override
            public void run(final @NotNull ProgressIndicator indicator) {
              installPackages(frameworkName, forceInstallFramework, indicator, requirement, sdk);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
              future.completeExceptionally(error);
            }

            @Override
            public void onSuccess() {
              future.complete(null);
              // Installed / checked successfully, call callback on AWT
              if (callback != null) {
                callback.run();
              }
            }
          });
      }
      else {
        ProgressManager.getInstance()
          .run(new Task.Modal(project, PyBundle.message("python.install.framework.ensure.installed", frameworkName), false) {
            @Override
            public void run(final @NotNull ProgressIndicator indicator) {
              installPackages(frameworkName, forceInstallFramework, indicator, requirement, sdk);
            }

            @Override
            public void onThrowable(@NotNull Throwable error) {
              future.completeExceptionally(error);
            }

            @Override
            public void onSuccess() {
              future.complete(null);
              // Installed / checked successfully, call callback on AWT
              if (callback != null) {
                callback.run();
              }
            }
          });
      }
    }
    else {
      future.complete(null);
      // No need to install, but still need to call callback on AWT
      if (callback != null) {
        assert SwingUtilities.isEventDispatchThread();
        callback.run();
      }
    }
    return future;
  }

  public @Nullable String getNewProjectPrefix() {
    return null;
  }

  public boolean supportsWelcomeScript() {
    return false;
  }

  /**
   * To be thrown if project can't be created on this sdk
   *
   * @author Ilya.Kazakevich
   */
  public static class PyNoProjectAllowedOnSdkException extends Exception {
    /**
     * @param reason why project can't be created
     */
    PyNoProjectAllowedOnSdkException(final @NotNull @DialogMessage String reason) {
      super(reason);
    }
  }

  private static void installPackages(final @NotNull String frameworkName,
                                      boolean forceInstallFramework,
                                      @NotNull ProgressIndicator indicator,
                                      final @NotNull String requirement,
                                      final @NotNull Sdk sdk) {
    final PyPackageManager packageManager = PyPackageManager.getInstance(sdk);
    boolean installed = false;
    if (!forceInstallFramework) {
      // First check if we need to do it
      indicator.setText(PyBundle.message("python.install.framework.checking.is.installed", frameworkName));
      final List<PyPackage> packages = PyPackageUtil.refreshAndGetPackagesModally(sdk);
      installed = PyPsiPackageUtil.findPackage(packages, requirement) != null;
    }

    if (!installed) {
      indicator.setText(PyBundle.message("python.install.framework.installing", frameworkName));
      try {
        packageManager.install(requirement);
        packageManager.refresh();
      }
      catch (final ExecutionException e) {
        reportPackageInstallationFailure(requirement, Pair.create(sdk, e));
      }
    }
  }

  public static boolean useNewInterpreterCreationUi() {
  return true;
  }
}
