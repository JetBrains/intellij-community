// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.execution.ExecutionException;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkAdditionalData;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.PathMappingSettings;
import com.intellij.util.Processor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.packaging.PyPackageManager;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.UnsupportedPythonSdkTypeException;
import com.jetbrains.python.sdk.skeletons.PySkeletonRefresher;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.io.File;
import java.time.Instant;
import java.util.List;
import java.util.*;

/**
 * Refreshes all project's Python SDKs.
 *
 * @author vlan
 * @author yole
 */
public class PythonSdkUpdater implements StartupActivity.Background {
  private static final Logger LOG = Logger.getInstance(PythonSdkUpdater.class);

  private static final Object ourLock = new Object();
  private static final Set<String> ourUnderRefresh = new HashSet<>();
  private static final Map<String, PyUpdateSdkRequestData> ourToBeRefreshed = new HashMap<>();

  static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.balloonGroup(
    "Python SDK Updater",
    PyBundle.message("python.sdk.updater.notifications.group.title"));

  /**
   * Schedules a background refresh of the SDKs of the modules for the open project.
   */
  @Override
  public void runActivity(@NotNull Project project) {
    if (!Registry.is("python.use.new.sdk.updater")) {
      PythonSdkUpdaterOld.updateProjectSdksOnStartup(project);
      return;
    }
    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) return;
    if (project.isDisposed()) return;

    for (Sdk sdk : getPythonSdks(project)) {
      scheduleUpdate(sdk, project);
    }
  }

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

    @NotNull
    private static PyUpdateSdkRequestData merge(@NotNull PyUpdateSdkRequestData oldRequest,
                                                @NotNull PyUpdateSdkRequestData newRequest) {
      return new PyUpdateSdkRequestData(oldRequest.myTimestamp, newRequest.myTraceback);
    }
  }

  private static class PyUpdateSdkTask extends Task.Backgroundable {

    private final @NotNull String mySdkKey;
    private final @NotNull PyUpdateSdkRequestData myRequestData;

    PyUpdateSdkTask(@NotNull Project project,
                    @NotNull String key,
                    @NotNull PyUpdateSdkRequestData requestData) {
      super(project, PyBundle.message("sdk.gen.updating.interpreter"), false);
      mySdkKey = key;
      myRequestData = requestData;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      if (myProject.isDisposed()) {
        return;
      }
      @Nullable Sdk sdk = PythonSdkUtil.findSdkByKey(mySdkKey);
      if (sdk == null) {
        LOG.warn("SDK for " + mySdkKey + " was removed from the SDK list");
        return;
      }
      if (sdk instanceof Disposable && Disposer.isDisposed((Disposable)sdk)) {
        return;
      }
      if (Trigger.LOG.isDebugEnabled()) {
        Trigger.LOG.debug("Starting SDK refresh for '" + mySdkKey + "' triggered by " + Trigger.getCauseByTrace(myRequestData.myTraceback));
      }
      try {
        updateLocalSdkVersionAndPaths(sdk, myProject);
        generateSkeletons(sdk, indicator);
        refreshPackages(sdk, indicator);
      }
      catch (InvalidSdkException e) {
        LOG.warn("Update for SDK " + sdk + " failed", e);
      }

      // restart code analysis
      ApplicationManager.getApplication().invokeLater(() -> DaemonCodeAnalyzer.getInstance(myProject).restart(), myProject.getDisposed());
    }

    private void refreshPackages(@NotNull Sdk sdk, @NotNull ProgressIndicator indicator) {
      try {
        LOG.info("Performing background scan of packages for SDK " + getSdkPresentableName(sdk));
        indicator.setIndeterminate(true);
        indicator.setText(PyBundle.message("python.sdk.scanning.installed.packages"));
        indicator.setText2("");
        PyPackageManager.getInstance(sdk).refreshAndGetPackages(true);
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

    private void generateSkeletons(@NotNull Sdk sdk, @NotNull ProgressIndicator indicator) {
      final String skeletonsPath = PythonSdkUtil.getSkeletonsPath(sdk);
      try {
        final String sdkPresentableName = getSdkPresentableName(sdk);
        LOG.info("Performing background update of skeletons for SDK " + sdkPresentableName);
        indicator.setText(PyBundle.message("python.sdk.updating.skeletons"));
        PySkeletonRefresher.refreshSkeletonsOfSdk(myProject, null, skeletonsPath, sdk);
        updateRemoteSdkPaths(sdk, getProject());
      }
      catch (UnsupportedPythonSdkTypeException e) {
        NOTIFICATION_GROUP
          .createNotification(PyBundle.message("sdk.gen.failed.notification.title"), null,
                              PyBundle.message("remote.interpreter.support.is.not.available", sdk.getName()),
                              NotificationType.WARNING)
          .notify(myProject);
      }
      catch (InvalidSdkException e) {
        if (PythonSdkUtil.isRemote(PythonSdkUtil.findSdkByKey(mySdkKey))) {
          PythonSdkType.notifyRemoteSdkSkeletonsFail(e, () -> {
            Sdk revalidatedSdk = PythonSdkUtil.findSdkByKey(mySdkKey);
            if (revalidatedSdk != null) {
              update(revalidatedSdk, myProject, null);
            }
          });
        }
        else if (!PythonSdkUtil.isInvalid(sdk)) {
          LOG.error(e);
        }
      }
    }


    @Override
    public void onFinished() {
      if (Trigger.LOG.isDebugEnabled()) {
        Trigger.LOG.debug("Finishing SDK refresh for '" + mySdkKey + "' " +
                          "originally scheduled at " + myRequestData.myTimestamp + " by " +
                          Trigger.getCauseByTrace(myRequestData.myTraceback));
      }
      PyUpdateSdkRequestData requestData;
      synchronized (ourLock) {
        boolean existed = ourUnderRefresh.remove(mySdkKey);
        LOG.assertTrue(existed, "Error in SDK refresh scheduling: refreshed SDK is not in the set.");
        requestData = ourToBeRefreshed.remove(mySdkKey);
        if (requestData != null) {
          ourUnderRefresh.add(mySdkKey);
        }
      }

      if (requestData != null) {
        ProgressManager.getInstance().run(new PyUpdateSdkTask(myProject, mySdkKey, requestData));
      }
    }
  }

  /**
   * @deprecated Use {@link #scheduleUpdate} or {@link #updateVersionAndPathsSynchronouslyAndScheduleRemaining}
   */
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.3")
  @Deprecated
  public static boolean update(@NotNull Sdk sdk, @Nullable Project project, @Nullable Component ownerComponent) {
    if (!Registry.is("python.use.new.sdk.updater")) {
      return PythonSdkUpdaterOld.update(sdk, project, ownerComponent);
    }
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
   * or changed in future. Unless you're sure that a synchronous update is necessary you should rather use
   * {@link #scheduleUpdate(Sdk, Project)} directly.
   *
   * @return false if there was an immediate problem updating the SDK. Other problems are reported as log entries and balloons.
   * @see #scheduleUpdate(Sdk, Project)
   */
  @ApiStatus.Internal
  public static boolean updateVersionAndPathsSynchronouslyAndScheduleRemaining(@NotNull Sdk sdk, @Nullable Project project) {
    if (!Registry.is("python.use.new.sdk.updater")) {
      return PythonSdkUpdaterOld.update(sdk, project, null);
    }

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
    if (application.isWriteThread() && !isSavedSdk) {
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
  @ApiStatus.Experimental
  public static void scheduleUpdate(@NotNull Sdk sdk, @NotNull Project project) {
    if (!Registry.is("python.use.new.sdk.updater")) {
      PythonSdkUpdaterOld.update(sdk, project, null);
      return;
    }
    scheduleUpdate(sdk, project, new PyUpdateSdkRequestData());
  }

  private static void scheduleUpdate(@NotNull Sdk sdk, @NotNull Project project, @NotNull PyUpdateSdkRequestData requestData) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.info("Skipping background update for '" + sdk + "' in unit test mode");
      return;
    }
    final String key = PythonSdkType.getSdkKey(sdk);
    synchronized (ourLock) {
      if (ourUnderRefresh.contains(key)) {
        if (Trigger.LOG.isDebugEnabled()) {
          PyUpdateSdkRequestData previousRequest = ourToBeRefreshed.get(key);
          if (previousRequest != null) {
            String cause = Trigger.getCauseByTrace(previousRequest.myTraceback);
            Trigger.LOG.debug("Discarding previous update for " + sdk + " triggered by " + cause);
          }
        }
        ourToBeRefreshed.merge(key, requestData, PyUpdateSdkRequestData::merge);
        return;
      }
      else {
        ourUnderRefresh.add(key);
      }
    }
    ProgressManager.getInstance().run(new PyUpdateSdkTask(project, key, requestData));
  }

  /**
   * Updates the SDK as {@link #updateVersionAndPathsSynchronouslyAndScheduleRemaining(Sdk, Project)} describes, but
   * shows an error message if the first synchronous part of the update fails.
   *
   * @see #updateVersionAndPathsSynchronouslyAndScheduleRemaining(Sdk, Project)
   */
  @ApiStatus.Internal
  public static void updateOrShowError(@NotNull Sdk sdk, @Nullable Project project, @Nullable Component ownerComponent) {
    if (!Registry.is("python.use.new.sdk.updater")) {
      PythonSdkUpdaterOld.updateOrShowError(sdk, project, ownerComponent);
      return;
    }
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

  private static void updateLocalSdkVersionAndPaths(@NotNull Sdk sdk, @Nullable Project project)
    throws InvalidSdkException {
    updateLocalSdkVersion(sdk);
    updateLocalSdkPaths(sdk, project);
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
      if (!StringUtil.equals(versionString, sdk.getVersionString())) {
        changeSdkModificator(sdk, modificatorToWrite -> {
          modificatorToWrite.setVersionString(versionString);
          return true;
        });
      }
    }
  }

  /**
   * Updates the paths of a local SDK.
   * <p>
   * May be invoked from any thread. May freeze the current thread while evaluating sys.path.
   */
  private static void updateLocalSdkPaths(@NotNull Sdk sdk, @Nullable Project project)
    throws InvalidSdkException {
    if (!PythonSdkUtil.isRemote(sdk)) {
      final boolean forceCommit = ensureBinarySkeletonsDirectoryExists(sdk);
      final List<VirtualFile> localSdkPaths = getLocalSdkPaths(sdk, project);
      commitSdkPathsIfChanged(sdk, localSdkPaths, forceCommit);
    }
  }

  /**
   * Updates the paths of a remote SDK.
   * <p>
   * Requires the skeletons refresh steps to be run before it in order to get remote paths mappings in the additional SDK data.
   * <p>
   * You may invoke it from any thread. Blocks until the commit is done in the AWT thread.
   */
  private static void updateRemoteSdkPaths(@NotNull Sdk sdk, @Nullable Project project) {
    if (PythonSdkUtil.isRemote(sdk)) {
      final boolean forceCommit = ensureBinarySkeletonsDirectoryExists(sdk);
      final List<VirtualFile> remoteSdkPaths = getRemoteSdkPaths(sdk, project);
      commitSdkPathsIfChanged(sdk, remoteSdkPaths, forceCommit);
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

  /**
   * Returns all the paths for a local SDK.
   */
  @NotNull
  private static List<VirtualFile> getLocalSdkPaths(@NotNull Sdk sdk, @Nullable Project project) throws InvalidSdkException {
    return ImmutableList.<VirtualFile>builder()
      .addAll(filterRootPaths(sdk, evaluateSysPath(sdk), project))
      .addAll(getSkeletonsPaths(sdk))
      .addAll(getUserAddedPaths(sdk))
      .addAll(PyTypeShed.INSTANCE.findRootsForSdk(sdk))
      .build();
  }

  /**
   * Returns all the paths for a remote SDK.
   * <p>
   * Requires the skeletons refresh steps to be run before it in order to get remote paths mappings in the additional SDK data.
   */
  @NotNull
  private static List<VirtualFile> getRemoteSdkPaths(@NotNull Sdk sdk, @Nullable Project project) {
    return ImmutableList.<VirtualFile>builder()
      .addAll(getRemoteSdkMappedPaths(sdk, project))
      .addAll(getSkeletonsPaths(sdk))
      .addAll(getUserAddedPaths(sdk))
      .addAll(PyTypeShed.INSTANCE.findRootsForSdk(sdk))
      .build();
  }

  /**
   * Returns all the paths manually added to an SDK by the user.
   */
  @NotNull
  private static List<VirtualFile> getUserAddedPaths(@NotNull Sdk sdk) {
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
  @NotNull
  private static List<VirtualFile> getRemoteSdkMappedPaths(@NotNull Sdk sdk, @Nullable Project project) {
    final SdkAdditionalData additionalData = sdk.getSdkAdditionalData();
    if (additionalData instanceof PyRemoteSdkAdditionalDataBase) {
      final PyRemoteSdkAdditionalDataBase remoteSdkData = (PyRemoteSdkAdditionalDataBase)additionalData;
      final List<String> paths = new ArrayList<>();
      for (PathMappingSettings.PathMapping mapping : remoteSdkData.getPathMappings().getPathMappings()) {
        paths.add(mapping.getLocalRoot());
      }
      return filterRootPaths(sdk, paths, project);
    }
    return Collections.emptyList();
  }

  /**
   * Filters valid paths from an initial set of Python paths and returns them as virtual files.
   */
  @NotNull
  public static List<VirtualFile> filterRootPaths(@NotNull Sdk sdk, @NotNull List<String> paths, @Nullable Project project) {
    final PythonSdkAdditionalData pythonAdditionalData = PyUtil.as(sdk.getSdkAdditionalData(), PythonSdkAdditionalData.class);
    final Collection<VirtualFile> excludedPaths = pythonAdditionalData != null ? pythonAdditionalData.getExcludedPathFiles() :
                                                  Collections.emptyList();
    final Set<VirtualFile> moduleRoots = new HashSet<>();
    if (project != null) {
      final Module[] modules = ModuleManager.getInstance(project).getModules();
      for (Module module : modules) {
        moduleRoots.addAll(PyUtil.getSourceRoots(module));
      }
    }
    final List<VirtualFile> results = new ArrayList<>();
    // TODO: Refactor SDK so they can provide exclusions for root paths
    final VirtualFile condaFolder = PythonSdkUtil.isConda(sdk) ? PythonSdkUtil.getCondaDirectory(sdk) : null;
    for (String path : paths) {
      if (path != null && !FileUtilRt.extensionEquals(path, "egg-info")) {
        final VirtualFile virtualFile = StandardFileSystems.local().refreshAndFindFileByPath(path);
        if (virtualFile != null && !virtualFile.equals(condaFolder)) {
          final VirtualFile rootFile = PythonSdkType.getSdkRootVirtualFile(virtualFile);
          if (!excludedPaths.contains(rootFile) && !moduleRoots.contains(rootFile)) {
            results.add(rootFile);
            continue;
          }
        }
      }
      LOG.info("Bogus sys.path entry " + path);
    }
    return results;
  }

  /**
   * Returns the paths of the binary skeletons and user skeletons for an SDK.
   */
  @NotNull
  private static List<VirtualFile> getSkeletonsPaths(@NotNull Sdk sdk) {
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

  @NotNull
  private static String getSdkPresentableName(@NotNull Sdk sdk) {
    final String homePath = sdk.getHomePath();
    final String name = sdk.getName();
    return homePath != null ? name + " (" + homePath + ")" : name;
  }

  /**
   * Evaluates sys.path by running the Python interpreter from a local SDK.
   * <p>
   * Returns all the existing paths except those manually excluded by the user.
   */
  @NotNull
  private static List<String> evaluateSysPath(@NotNull Sdk sdk) throws InvalidSdkException {
    if (PythonSdkUtil.isRemote(sdk)) {
      throw new IllegalArgumentException("Cannot evaluate sys.path for remote Python interpreter " + sdk);
    }
    final long startTime = System.currentTimeMillis();
    ProgressManager.progress(PyBundle.message("sdk.updating.interpreter.paths"));
    final List<String> sysPath = PythonSdkType.getSysPath(sdk);
    LOG.info("Updating sys.path took " + (System.currentTimeMillis() - startTime) + " ms");
    return sysPath;
  }

  /**
   * Commits new SDK paths using an SDK modificator if the paths have been changed.
   * <p>
   * You may invoke it from any thread. Blocks until the commit is done in the AWT thread.
   */
  private static void commitSdkPathsIfChanged(@NotNull Sdk sdk,
                                              @NotNull final List<VirtualFile> sdkPaths,
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
        effectiveModificator.commitChanges();
      }
    });
  }

  /**
   * Returns unique Python SDKs for the open modules of the project.
   */
  @NotNull
  private static Set<Sdk> getPythonSdks(@NotNull Project project) {
    final Set<Sdk> pythonSdks = new LinkedHashSet<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      final Sdk sdk = PythonSdkUtil.findPythonSdk(module);
      if (sdk != null && sdk.getSdkType() instanceof PythonSdkType) {
        pythonSdks.add(sdk);
      }
    }
    return pythonSdks;
  }

  private enum Trigger {
    STARTUP_ACTIVITY("com.jetbrains.python.sdk.PythonSdkUpdater.runActivity"),
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

    @NotNull
    public static String getCauseByTrace(@NotNull Throwable trace) {
      final Trigger trigger = findTriggerByTrace(trace);
      if (trigger != null) {
        return trigger.name();
      }
      return "Unknown trigger:\n" + ExceptionUtil.getThrowableText(trace);
    }

    @Nullable
    public static Trigger findTriggerByTrace(@NotNull Throwable trace) {
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
