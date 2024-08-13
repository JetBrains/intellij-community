// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.ExecutionException;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.observation.TrackingUtil;
import com.intellij.remote.RemoteSdkProperties;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.Processor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.PythonPluginDisposable;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.packaging.management.PythonPackageManager;
import com.jetbrains.python.packaging.management.PythonPackageManagerExt;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.remote.UnsupportedPythonSdkTypeException;
import com.jetbrains.python.sdk.headless.PythonActivityKey;
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.awt.*;
import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.*;
import java.util.function.Function;

/**
 * Refreshes all project's Python SDKs.
 */
public final class PythonSdkUpdater {
  private static final Logger LOG = Logger.getInstance(PythonSdkUpdater.class);

  private static final Object ourLock = new Object();
  private static final Set<Sdk> ourUnderRefresh = new HashSet<>();
  private static final Map<Sdk, PyUpdateSdkRequestData> ourToBeRefreshed = new HashMap<>();
  private static final String NOTIFICATION_GROUP_ID = "Python SDK Updater";
  private static volatile boolean ourEnabledInTests = false;

  @ApiStatus.Internal
  @TestOnly
  public static void setEnabledInTests(boolean enabled) {
    ourEnabledInTests = enabled;
  }

  /**
   * Schedules a background refresh of the SDKs of the modules for the open project.
   */
  private static class PyUpdateSdkRequestData {
    final Instant myTimestamp;
    final Throwable myTraceback;

    private PyUpdateSdkRequestData() {
      this(Instant.now(), new Throwable());
    }

    private PyUpdateSdkRequestData(@NotNull Instant timestamp, @NotNull Throwable traceback) {
      myTimestamp = timestamp;
      myTraceback = traceback;
    }

    private static @NotNull PyUpdateSdkRequestData merge(@NotNull PyUpdateSdkRequestData oldRequest,
                                                         @NotNull PyUpdateSdkRequestData newRequest) {
      return new PyUpdateSdkRequestData(oldRequest.myTimestamp, newRequest.myTraceback);
    }
  }

  private static class PyUpdateSdkTask extends Task.Backgroundable {

    private final @NotNull Sdk mySdk;
    private final @NotNull PyUpdateSdkRequestData myRequestData;

    @SuppressWarnings("FieldNameHidesFieldInSuperclass")  // Only notnull
    private final @NotNull Project myProject;

    PyUpdateSdkTask(@NotNull Project project,
                    @NotNull Sdk sdk,
                    @NotNull PyUpdateSdkRequestData requestData) {
      super(project, PyBundle.message("sdk.gen.updating.interpreter"), false);
      mySdk = sdk;
      myRequestData = requestData;
      myProject = project;
    }

    private boolean isSdkDisposed() {
      return mySdk instanceof Disposable && Disposer.isDisposed((Disposable)mySdk);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      if (myProject.isDisposed()) {
        return;
      }
      if (isSdkDisposed()) {
        return;
      }
      // This explicit cancellation should become unnecessary on migrating PythonSdkUpdater to coroutines and withBackgroundProgress
      Disposable indicatorDisposable = getIndicatorDisposable(indicator);
      Disposer.register(PythonPluginDisposable.getInstance(myProject), indicatorDisposable);
      if (Trigger.LOG.isDebugEnabled()) {
        Trigger.LOG.debug(
          "Starting SDK refresh for '" + mySdk.getName() + "' triggered by " + Trigger.getCauseByTrace(myRequestData.myTraceback));
      }
      try {
        PyTargetsIntrospectionFacade targetsFacade = new PyTargetsIntrospectionFacade(mySdk, myProject);
        String version = targetsFacade.getInterpreterVersion(indicator);
        commitSdkVersionIfChanged(mySdk, version);
        if (targetsFacade.isLocalTarget()) {
          List<String> paths = targetsFacade.getInterpreterPaths(indicator);
          updateSdkPaths(mySdk, paths, myProject);
        }
        else {
          targetsFacade.synchronizeRemoteSourcesAndSetupMappings(indicator);
        }
        // This step also includes setting mapped interpreter paths
        generateSkeletons(mySdk, indicator);
        refreshPackages(mySdk, indicator);
      }
      catch (ExecutionException e) {
        LOG.warn("Update for SDK " + mySdk.getName() + " failed", e);
      }
      finally {
        ApplicationManager.getApplication().invokeLater(() -> {
          Disposer.dispose(indicatorDisposable);
          // restart code analysis
          DaemonCodeAnalyzer.getInstance(myProject).restart();
        }, myProject.getDisposed());
      }
    }

    private @NotNull Disposable getIndicatorDisposable(@NotNull ProgressIndicator indicator) {
      return indicator instanceof Disposable disposable ? disposable : new Disposable() {
        @Override
        public void dispose() {
          LOG.info("Cancelling update for " + mySdk);
          if (indicator.isRunning()) {
            indicator.cancel();
          }
        }
      };
    }

    private void refreshPackages(@NotNull Sdk sdk, @NotNull ProgressIndicator indicator) {
      try {
        LOG.info("Performing background scan of packages for SDK " + getSdkPresentableName(sdk));
        indicator.setIndeterminate(true);
        indicator.setText(PyBundle.message("python.sdk.scanning.installed.packages"));
        indicator.setText2("");
        PyPackageManager.getInstance(sdk).refreshAndGetPackages(true);
        PythonPackageManager manager = PythonPackageManager.Companion.forSdk(myProject, mySdk);
        PythonPackageManagerExt.launchReload(manager);
      }
      catch (ExecutionException e) {
        if (LOG.isDebugEnabled()) {
          e.initCause(myRequestData.myTraceback);
          LOG.debug(e);
        }
        else {
          LOG.warn(e.getMessage());
        }
      }
    }

    /**
     * May be invoked from any thread.
     */
    private void generateSkeletons(@NotNull Sdk sdk, @NotNull ProgressIndicator indicator) {
      final String skeletonsPath = PythonSdkUtil.getSkeletonsPath(sdk);
      try {
        final String sdkPresentableName = getSdkPresentableName(sdk);
        LOG.info("Performing background update of skeletons for SDK " + sdkPresentableName);
        indicator.setText(PyBundle.message("python.sdk.updating.skeletons"));
        PySkeletonRefresher.refreshSkeletonsOfSdk(myProject, null, skeletonsPath, sdk);
        if (PythonSdkUtil.isRemote(sdk)) {
          updateSdkPaths(sdk, getRemoteSdkMappedPaths(sdk), getProject());
        }
      }
      catch (UnsupportedPythonSdkTypeException | InvalidSdkException e) {
        notifyOfGenerationFailure(e, sdk);
      }
    }

    private void notifyOfGenerationFailure(@NotNull Exception exception, @NotNull Sdk sdk) {
      if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
        LOG.warn(exception);
        return;
      }
      if (exception instanceof UnsupportedPythonSdkTypeException) {
        NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
          .createNotification(PyBundle.message("sdk.gen.failed.notification.title"),
                              PyBundle.message("remote.interpreter.support.is.not.available", sdk.getName()),
                              NotificationType.WARNING)
          .notify(myProject);
      }
      else if (exception instanceof InvalidSdkException) {
        if (PythonSdkUtil.isRemote(mySdk)) {
          PythonSdkType.notifyRemoteSdkSkeletonsFail((InvalidSdkException)exception, () -> {
            if (!isSdkDisposed()) {
              update(mySdk, myProject, null);
            }
          });
        }
        else if (PySdkExtKt.getSdkSeemsValid(sdk)) {
          LOG.error(exception);
        }
      }
    }


    @Override
    public void onFinished() {
      if (Trigger.LOG.isDebugEnabled()) {
        Trigger.LOG.debug("Finishing SDK refresh for '" + mySdk.getName() + "' " +
                          "originally scheduled at " + myRequestData.myTimestamp + " by " +
                          Trigger.getCauseByTrace(myRequestData.myTraceback));
      }
      PyUpdateSdkRequestData requestData;
      synchronized (ourLock) {
        boolean existed = ourUnderRefresh.remove(mySdk);
        LOG.assertTrue(existed, "Error in SDK refresh scheduling: refreshed SDK is not in the set.");
        requestData = ourToBeRefreshed.remove(mySdk);
        if (requestData != null) {
          ourUnderRefresh.add(mySdk);
        }
      }

      if (requestData != null) {
        ProgressManager.getInstance().run(new PyUpdateSdkTask(myProject, mySdk, requestData));
      }
    }
  }

  /**
   * @deprecated Use {@link #scheduleUpdate} or {@link #updateVersionAndPathsSynchronouslyAndScheduleRemaining}
   */
  @ApiStatus.Internal
  @Deprecated
  public static boolean update(@NotNull Sdk sdk, @Nullable Project project, @Nullable Component ownerComponent) {
    return updateVersionAndPathsSynchronouslyAndScheduleRemaining(sdk, project);
  }

  /**
   * <i>Synchronously</i> update an interpreter version and paths in {@link ProjectJdkTable} and schedule a full-scale background refresh
   * with {@link #scheduleUpdate(Sdk, Project)}.
   * <p>
   * For a local SDK, any version and paths changes are automatically committed. For a remote SDK, paths and path mappings are queried
   * and saved in the background task after the skeleton generation finishes.
   * <p>
   * Since this method blocks for the first phase of an update, it's not allowed to call it on threads holding a read or write action.
   * The only exception is made for EDT, in which case a modal progress indicator will be displayed during this first synchronous step.
   * <p>
   * This method emulates the legacy behavior of {@link #update(Sdk, Project, Component)} and is likely to be removed
   * or changed in the future. Unless you're sure that a synchronous update is necessary you should rather use
   * {@link #scheduleUpdate(Sdk, Project)} directly.
   *
   * @return false if there was an immediate problem updating the SDK. Other problems are reported as log entries and balloons.
   * @see #scheduleUpdate(Sdk, Project)
   */
  @ApiStatus.Internal
  public static boolean updateVersionAndPathsSynchronouslyAndScheduleRemaining(@NotNull Sdk sdk, @Nullable Project project) {
    Application application = ApplicationManager.getApplication();
    try {
      // This is not optimal but already happens in many contexts including possible external usages, e.g. during a new SDK generation.
      if (application.isDispatchThread()) {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
          updateLocalSdkVersionAndPaths(sdk, project);
          return null;
        }, PyBundle.message("sdk.gen.updating.interpreter"), false, project);
      }
      else {
        LOG.assertTrue(!application.isReadAccessAllowed(), "Synchronous SDK update should not be run under read action");
        updateLocalSdkVersionAndPaths(sdk, project);
      }
    }
    catch (InvalidSdkException e) {
      LOG.warn("Error while evaluating path and version: ", e);
      return false;
    }
    if (project == null) {
      return true;
    }
    // Don't inline this variable, it needs to anchor the current stack.
    PyUpdateSdkRequestData request = new PyUpdateSdkRequestData();
    // When a new interpreter is still being generated, we need to wait until it finishes and SDK
    // is properly written in ProjectJdkTable. Otherwise, a concurrent background update might fail.
    boolean isSavedSdk = PythonSdkUtil.findSdkByKey(PythonSdkType.getSdkKey(sdk)) != null;
    if (application.isWriteIntentLockAcquired() && !isSavedSdk) {
      application.invokeLaterOnWriteThread(() -> scheduleUpdate(sdk, project, request));
    }
    else {
      scheduleUpdate(sdk, project, request);
    }
    return true;
  }


  /**
   * Schedule an <i>asynchronous</i> background update of the given SDK.
   * <p>
   * This method may be invoked from any thread. Synchronization guarantees the following properties:
   * <ul>
   *   <li>No two updates of the same SDK can be performed simultaneously.</li>
   *   <li>Subsequent requests to update an SDK already being refreshed will be queued and launched as soon as the ongoing update finishes.</li>
   *   <li>Multiple subsequent requests to update an SDK already being refreshed will be combined and result in a single update operation.</li>
   * </ul>
   */
  public static void scheduleUpdate(@NotNull Sdk sdk, @NotNull Project project) {
    scheduleUpdate(sdk, project, new PyUpdateSdkRequestData());
  }

  /**
   * Does the same that {@link #scheduleUpdate(Sdk, Project)}, but simply omits the new update request if another one is in progress,
   * while the former method will schedule the next update after processing the other update.
   */
  public static void ensureUpdateScheduled(@NotNull Sdk sdk, @NotNull Project project) {
    synchronized (ourLock) {
      if (ourUnderRefresh.contains(sdk) || ourToBeRefreshed.containsKey(sdk)) return;
      ourUnderRefresh.add(sdk);
    }
    ProgressManager.getInstance().run(new PyUpdateSdkTask(project, sdk, new PyUpdateSdkRequestData()));
  }

  public static boolean isUpdateScheduled(@NotNull Sdk sdk) {
    synchronized (ourLock) {
      return ourUnderRefresh.contains(sdk) || ourToBeRefreshed.containsKey(sdk);
    }
  }

  private static void scheduleUpdate(@NotNull Sdk sdk, @NotNull Project project, @NotNull PyUpdateSdkRequestData requestData) {
    if (!ourEnabledInTests && ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.info("Skipping background update for '" + sdk + "' in unit test mode");
      return;
    }
    TrackingUtil.trackActivity(project, PythonActivityKey.INSTANCE, () -> {
      synchronized (ourLock) {
        if (ourUnderRefresh.contains(sdk)) {
          if (Trigger.LOG.isDebugEnabled()) {
            PyUpdateSdkRequestData previousRequest = ourToBeRefreshed.get(sdk);
            if (previousRequest != null) {
              String cause = Trigger.getCauseByTrace(previousRequest.myTraceback);
              Trigger.LOG.debug("Discarding previous update for " + sdk + " triggered by " + cause);
            }
          }
          ourToBeRefreshed.merge(sdk, requestData, PyUpdateSdkRequestData::merge);
          return;
        }
        else {
          ourUnderRefresh.add(sdk);
        }
      }
      ProgressManager.getInstance().run(new PyUpdateSdkTask(project, sdk, requestData));
    });
  }

  /**
   * Updates the SDK as {@link #updateVersionAndPathsSynchronouslyAndScheduleRemaining(Sdk, Project)} describes, but
   * shows an error message if the first synchronous part of the update fails.
   *
   * @see #updateVersionAndPathsSynchronouslyAndScheduleRemaining(Sdk, Project)
   */
  @ApiStatus.Internal
  public static void updateOrShowError(@NotNull Sdk sdk, @Nullable Project project, @Nullable Component ownerComponent) {
    boolean versionAndPathsUpdated = updateVersionAndPathsSynchronouslyAndScheduleRemaining(sdk, project);
    if (!versionAndPathsUpdated) {
      ApplicationManager.getApplication().invokeLater(
        () ->
          Messages.showErrorDialog(
            project,
            PyBundle.message("python.sdk.cannot.setup.sdk", getSdkPresentableName(sdk)),
            PyBundle.message("python.sdk.invalid.python.sdk")
          )
      );
    }
  }

  /**
   * May be invoked from any thread. May freeze the current thread while evaluating python version and sys.path.
   */
  private static void updateLocalSdkVersionAndPaths(@NotNull Sdk sdk, @Nullable Project project)
    throws InvalidSdkException {
    updateLocalSdkVersion(sdk);
    if (!PythonSdkUtil.isRemote(sdk)) {
      try {
        updateSdkPaths(sdk, evaluateSysPath(sdk, project != null ? project : ProjectManager.getInstance().getDefaultProject()), project);
      }
      catch (ExecutionException e) {
        throw new InvalidSdkException(PyBundle.message("python.sdk.cannot.evaluate.sdk.version.error.message"), e);
      }
    }
  }

  /**
   * Changes the version string of an SDK if it's out of date.
   * <p>
   * May be invoked from any thread. May freeze the current thread while evaluating the run-time Python version.
   */
  private static void updateLocalSdkVersion(@NotNull Sdk sdk) {
    if (!PythonSdkUtil.isRemote(sdk)) {
      ProgressManager.progress(PyBundle.message("sdk.updating.interpreter.version"));
      final String versionString = sdk.getSdkType().getVersionString(sdk);
      commitSdkVersionIfChanged(sdk, versionString);
    }
  }

  private static void commitSdkVersionIfChanged(@NotNull Sdk sdk, @Nullable String versionString) {
    if (!StringUtil.equals(versionString, sdk.getVersionString())) {
      changeSdkModificator(sdk, modificatorToWrite -> {
        modificatorToWrite.setVersionString(versionString);
        return true;
      });
    }
  }

  private static void updateSdkPaths(@NotNull Sdk sdk, @NotNull List<String> paths, @Nullable Project project) {
    final var moduleRoots = getModuleRoots(project);
    final var excludedPaths = getExcludedPaths(sdk);
    final var sdkRoots = splitIntoLibraryAndSourceRoots(sdk, paths, moduleRoots, excludedPaths, it -> sdkPathToRoot(sdk, it));
    final var userAddedRoots = splitIntoLibraryAndSourceRoots(sdk, getUserAddedPaths(sdk), moduleRoots, excludedPaths, Function.identity());

    final boolean forceCommit = ensureBinarySkeletonsDirectoryExists(sdk);
    final List<VirtualFile> localSdkPaths = buildSdkPaths(sdk, sdkRoots.first, userAddedRoots.first);
    commitSdkPathsIfChanged(sdk, localSdkPaths, forceCommit);

    final var pathsToTransfer = new HashSet<VirtualFile>();
    pathsToTransfer.addAll(sdkRoots.second);
    pathsToTransfer.addAll(userAddedRoots.second);
    // Presumably source and content roots that were configured manually by user, not set up automatically as "transferred"
    HashSet<VirtualFile> nonTransferredModuleRoots = new HashSet<>(moduleRoots);
    nonTransferredModuleRoots.removeAll(PyTransferredSdkRootsKt.getPathsToTransfer(sdk));
    pathsToTransfer.removeAll(nonTransferredModuleRoots);

    /*
    PyTransferredSdkRootsKt#transferRoots and PyTransferredSdkRootsKt#removeTransferredRoots skip sdks
    that are not equal to module one (editable as well).

    That's why roots changes were not applied but paths to transfer were successfully set.

    When current method was executed for original sdk,
    roots changes were not applied since there were no changes in paths to transfer (they were shared with editable copy).
     */
    if (!pathsToTransfer.equals(PyTransferredSdkRootsKt.getPathsToTransfer(sdk))) {
      if (project != null) {
        PyTransferredSdkRootsKt.removeTransferredRootsFromModulesWithSdk(project, sdk);
      }
      PyTransferredSdkRootsKt.setPathsToTransfer(sdk, pathsToTransfer);
      if (project != null) {
        PyTransferredSdkRootsKt.transferRootsToModulesWithSdk(project, sdk);
      }
    }
  }

  private static boolean ensureBinarySkeletonsDirectoryExists(Sdk sdk) {
    final String skeletonsPath = PythonSdkUtil.getSkeletonsPath(sdk);
    if (skeletonsPath != null) {
      if (new File(skeletonsPath).mkdirs()) {
        return true;
      }
    }
    return false;
  }

  private static @NotNull List<VirtualFile> buildSdkPaths(@NotNull Sdk sdk,
                                                          @NotNull List<VirtualFile> sdkRoots,
                                                          @NotNull List<VirtualFile> userAddedRoots) {
    return ImmutableList.<VirtualFile>builder()
      .addAll(sdkRoots)
      .addAll(getSkeletonsPaths(sdk))
      .addAll(userAddedRoots)
      .addAll(PyTypeShed.INSTANCE.findRootsForSdk(sdk))
      .addAll(getBundledStubs())
      .build();
  }

  private static @NotNull List<VirtualFile> getBundledStubs() {
    VirtualFile bundledStubRoot = StandardFileSystems.local().findFileByPath(PythonHelpersLocator.findPathStringInHelpers("bundled_stubs"));
    if (bundledStubRoot == null) return Collections.emptyList();
    return List.of(bundledStubRoot);
  }

  /**
   * Returns all the paths manually added to an SDK by the user.
   */
  private static @NotNull List<VirtualFile> getUserAddedPaths(@NotNull Sdk sdk) {
    final SdkAdditionalData additionalData = sdk.getSdkAdditionalData();
    final PythonSdkAdditionalData pythonAdditionalData = PyUtil.as(additionalData, PythonSdkAdditionalData.class);
    return pythonAdditionalData != null ? Lists.newArrayList(pythonAdditionalData.getAddedPathFiles()) :
           Collections.emptyList();
  }

  /**
   * Returns local paths for a remote SDK that have been mapped to remote paths during the skeleton refresh step.
   * <p>
   * Returns all the existing paths except those manually excluded by the user.
   */
  private static @NotNull List<String> getRemoteSdkMappedPaths(@NotNull Sdk sdk) {
    final SdkAdditionalData additionalData = sdk.getSdkAdditionalData();
    if (additionalData instanceof RemoteSdkProperties remoteSdkData) {
      final List<String> paths = new ArrayList<>();
      for (PathMappingSettings.PathMapping mapping : remoteSdkData.getPathMappings().getPathMappings()) {
        paths.add(mapping.getLocalRoot());
      }
      return paths;
    }
    return Collections.emptyList();
  }

  private static <T> @NotNull Pair<@NotNull List<VirtualFile>, @NotNull List<VirtualFile>> splitIntoLibraryAndSourceRoots(@NotNull Sdk sdk,
                                                                                                                          @NotNull List<T> paths,
                                                                                                                          @NotNull Set<VirtualFile> moduleRoots,
                                                                                                                          @NotNull Collection<VirtualFile> excludedPaths,
                                                                                                                          @NotNull Function<T, @Nullable VirtualFile> mapper) {
    final List<VirtualFile> lib = new ArrayList<>();
    final List<VirtualFile> source = new ArrayList<>();
    for (T path : paths) {
      final VirtualFile rootFile = mapper.apply(path);
      if (rootFile != null && !excludedPaths.contains(rootFile)) {
        if (isUnderModuleRootsButNotSdk(rootFile, moduleRoots, sdk)) {
          source.add(rootFile);
        }
        else {
          lib.add(rootFile);
        }
        continue;
      }
      LOG.info("Bogus sys.path entry " + path);
    }
    return Pair.createNonNull(lib, source);
  }

  @ApiStatus.Internal
  public static @NotNull Set<VirtualFile> getModuleRoots(@Nullable Project project) {
    if (project != null) {
      final Set<VirtualFile> moduleRoots = new HashSet<>();
      final Module[] modules = ModuleManager.getInstance(project).getModules();
      for (Module module : modules) {
        moduleRoots.addAll(PyUtil.getSourceRoots(module));
      }
      return moduleRoots;
    }
    return Collections.emptySet();
  }

  private static @NotNull Collection<VirtualFile> getExcludedPaths(@NotNull Sdk sdk) {
    final PythonSdkAdditionalData pythonAdditionalData = PyUtil.as(sdk.getSdkAdditionalData(), PythonSdkAdditionalData.class);
    return pythonAdditionalData != null ? pythonAdditionalData.getExcludedPathFiles() : Collections.emptyList();
  }

  private static @Nullable VirtualFile sdkPathToRoot(@NotNull Sdk sdk, @Nullable String path) {
    if (path != null && !FileUtilRt.extensionEquals(path, "egg-info")) {
      // TODO: Refactor SDK so they can provide exclusions for root paths
      final VirtualFile condaFolder = PythonSdkUtil.isConda(sdk) ? PythonSdkUtil.getCondaDirectory(sdk) : null;

      final VirtualFile virtualFile = StandardFileSystems.local().refreshAndFindFileByPath(path);
      if (virtualFile != null && !virtualFile.equals(condaFolder)) {
        return PythonSdkType.getSdkRootVirtualFile(virtualFile);
      }
    }

    return null;
  }

  private static boolean isUnderModuleRootsButNotSdk(@NotNull VirtualFile file, @NotNull Set<VirtualFile> moduleRoots, @NotNull Sdk sdk) {
    if (VfsUtilCore.isUnder(file, moduleRoots)) {
      final VirtualFile envRoot = PySdkExtKt.getInnerVirtualEnvRoot(sdk);
      if (envRoot == null) {
        return true;
      }

      return !VfsUtilCore.isAncestor(envRoot, file, false);
    }

    return false;
  }

  /**
   * Returns the paths of the binary skeletons and user skeletons for an SDK.
   */
  private static @NotNull List<VirtualFile> getSkeletonsPaths(@NotNull Sdk sdk) {
    final List<VirtualFile> results = new ArrayList<>();
    final String skeletonsPath = PythonSdkUtil.getSkeletonsPath(sdk);
    if (skeletonsPath != null) {
      final VirtualFile skeletonsDir = StandardFileSystems.local().refreshAndFindFileByPath(skeletonsPath);
      if (skeletonsDir != null) {
        results.add(skeletonsDir);
        LOG.info("Binary skeletons directory for SDK " + getSdkPresentableName(sdk) + "): " + skeletonsDir.getPath());
      }
    }
    final VirtualFile userSkeletonsDir = PyUserSkeletonsUtil.getUserSkeletonsDirectory();
    if (userSkeletonsDir != null) {
      results.add(userSkeletonsDir);
      LOG.info("User skeletons directory for SDK " + getSdkPresentableName(sdk) + "): " + userSkeletonsDir.getPath());
    }
    return results;
  }

  private static @NotNull String getSdkPresentableName(@NotNull Sdk sdk) {
    final String homePath = sdk.getHomePath();
    final String name = sdk.getName();
    return homePath != null ? name + " (" + homePath + ")" : name;
  }

  /**
   * Evaluates {@code sys.path} by running the Python interpreter from  SDK.
   * <p>
   * Returns all the existing paths except those manually excluded by the user.
   */
  private static @NotNull List<String> evaluateSysPath(@NotNull Sdk sdk, @NotNull Project project) throws ExecutionException {
    final long startTime = System.currentTimeMillis();
    ProgressManager.progress(PyBundle.message("sdk.updating.interpreter.paths"));
    if (ApplicationManager.getApplication().isUnitTestMode() && PythonSdkType.isMock(sdk)) {
      // Mock sdk in tests can't be executed
      return PythonSdkType.getMockPath(sdk);
    }
    final List<String> sysPath = new PyTargetsIntrospectionFacade(sdk, project).getInterpreterPaths(new EmptyProgressIndicator());
    LOG.info("Updating sys.path took " + (System.currentTimeMillis() - startTime) + " ms");
    return sysPath;
  }

  /**
   * Commits new SDK paths using an SDK modificator if the paths have been changed.
   * <p>
   * You may invoke it from any thread. Blocks until the commit is done in the AWT thread.
   */
  private static void commitSdkPathsIfChanged(@NotNull Sdk sdk,
                                              final @NotNull List<VirtualFile> sdkPaths,
                                              boolean forceCommit) {
    final List<VirtualFile> currentSdkPaths = Arrays.asList(sdk.getRootProvider().getFiles(OrderRootType.CLASSES));
    if (forceCommit || !Sets.newHashSet(sdkPaths).equals(Sets.newHashSet(currentSdkPaths))) {
      changeSdkModificator(sdk, effectiveModificator -> {
        effectiveModificator.removeAllRoots();
        for (VirtualFile sdkPath : sdkPaths) {
          effectiveModificator.addRoot(PythonSdkType.getSdkRootVirtualFile(sdkPath), OrderRootType.CLASSES);
        }
        return true;
      });
    }
  }

  /**
   * Applies a processor to an SDK modificator or an SDK and commits it.
   * <p>
   * You may invoke it from any threads. Blocks until the commit is done in the AWT thread.
   */
  private static void changeSdkModificator(@NotNull Sdk sdk, @NotNull Processor<? super SdkModificator> processor) {
    TransactionGuard.getInstance().assertWriteSafeContext(ModalityState.defaultModalityState());
    ApplicationManager.getApplication().invokeAndWait(() -> {
      final SdkModificator effectiveModificator = sdk.getSdkModificator();
      if (processor.process(effectiveModificator)) {
        ApplicationManager.getApplication().runWriteAction(() -> effectiveModificator.commitChanges());
      }
    });
  }

  /**
   * Returns unique Python SDKs for the open modules of the project.
   */
  static @NotNull Set<Sdk> getPythonSdks(@NotNull Project project) {
    final Set<Sdk> pythonSdks = new LinkedHashSet<>();

    ReadAction.run(
      () ->
      {
        for (Module module : ModuleManager.getInstance(project).getModules()) {
          final Sdk sdk = PythonSdkUtil.findPythonSdk(module);
          if (sdk != null && sdk.getSdkType() instanceof PythonSdkType) {
            pythonSdks.add(sdk);
          }
        }
      }
    );

    return pythonSdks;
  }

  private enum Trigger {
    STARTUP_ACTIVITY("com.jetbrains.python.sdk.PythonSdkUpdateProjectActivity.execute"),
    CHANGE_UNDER_INTERPRETER_ROOTS("com.jetbrains.python.packaging.PyPackageManagerImpl.lambda$subscribeToLocalChanges"),
    REFRESH_AFTER_PACKAGING_OPERATION("com.jetbrains.python.packaging.PyPackageManagerImpl.lambda$refresh"),
    NEW_SDK_GENERATION("com.jetbrains.python.sdk.PySdkExtKt.createSdkByGenerateTask"),
    CHANGED_SDK_CONFIGURATION("com.jetbrains.python.configuration.PyActiveSdkConfigurable.apply"),
    SDK_RELOAD_IN_SETTINGS("com.jetbrains.python.configuration.PythonSdkDetailsDialog.reloadSdk"),
    START_SDK_UPDATES_ACTION("com.jetbrains.python.sdk.PyUpdateProjectSdkAction.lambda$actionPerformed");

    private static final Logger LOG = Logger.getInstance(Trigger.class);

    private final String myFrameMarker;

    Trigger(@NotNull String frameMarker) {
      myFrameMarker = frameMarker;
    }

    public static @NotNull String getCauseByTrace(@NotNull Throwable trace) {
      final Trigger trigger = findTriggerByTrace(trace);
      if (trigger != null) {
        return trigger.name();
      }
      return "Unknown trigger:\n" + ExceptionUtil.getThrowableText(trace);
    }

    public static @Nullable Trigger findTriggerByTrace(@NotNull Throwable trace) {
      final String traceText = ExceptionUtil.getThrowableText(trace);
      for (Trigger value : values()) {
        if (traceText.contains(value.myFrameMarker)) {
          return value;
        }
      }
      return null;
    }
  }
}
