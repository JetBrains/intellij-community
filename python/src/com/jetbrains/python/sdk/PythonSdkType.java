// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.ide.DataManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.KeyWithDefaultValue;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.reference.SoftReference;
import com.intellij.remote.ExceptionFix;
import com.intellij.remote.VagrantNotStartedException;
import com.intellij.remote.ext.LanguageCaseCollector;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.PlatformUtils;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.icons.PythonPsiApiIcons;
import com.jetbrains.python.remote.PyCredentialsContribution;
import com.jetbrains.python.remote.PyRemoteInterpreterUtil;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.sdk.add.PyAddSdkDialog;
import com.jetbrains.python.sdk.add.target.PyDetectedSdkAdditionalData;
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.target.PyInterpreterVersionUtil;
import com.jetbrains.python.target.PyTargetAwareAdditionalData;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.file.Paths;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.execution.target.TargetBasedSdks.loadTargetConfiguration;

/**
 * Class should be final and singleton since some code checks its instance by ref.
 */
public final class PythonSdkType extends SdkType {

  @ApiStatus.Internal public static final @NotNull Key<List<String>> MOCK_SYS_PATH_KEY = Key.create("PY_MOCK_SYS_PATH_KEY");

  @ApiStatus.Internal public static final @NotNull Key<String> MOCK_PY_VERSION_KEY = Key.create("PY_MOCK_PY_VERSION_KEY");

  @ApiStatus.Internal public static final @NotNull Key<Boolean> MOCK_PY_MARKER_KEY = KeyWithDefaultValue.create("MOCK_PY_MARKER_KEY", true);

  private static final Logger LOG = Logger.getInstance(PythonSdkType.class);

  private static final int MINUTE = 60 * 1000; // 60 seconds, used with script timeouts
  private static final @NonNls String SKELETONS_TOPIC = "Skeletons";

  private static final Key<WeakReference<Component>> SDK_CREATOR_COMPONENT_KEY = Key.create("#com.jetbrains.python.sdk.creatorComponent");


  /**
   * Old configuration may have this prefix in homepath. We must remove it
   */
  private static final @NotNull String LEGACY_TARGET_PREFIX = "target://";

  public static PythonSdkType getInstance() {
    return SdkType.findInstance(PythonSdkType.class);
  }

  private PythonSdkType() {
    super(PyNames.PYTHON_SDK_ID_NAME); //don't change this call as the string used for comparison
  }

  @Override
  public Icon getIcon() {
    return PythonPsiApiIcons.Python;
  }

  @Override
  public @NotNull String getHelpTopic() {
    return "reference.project.structure.sdk.python";
  }

  @Override
  public @NonNls @Nullable String suggestHomePath() {
    return null;
  }

  @Override
  public @NotNull Collection<String> suggestHomePaths() {
    final Sdk[] existingSdks = ReadAction.compute(() -> ProjectJdkTable.getInstance().getAllJdks());
    final List<PyDetectedSdk> sdks = PySdkExtKt.detectSystemWideSdks(null, Arrays.asList(existingSdks));
    //return all detected items after PY-41218 is fixed
    final PyDetectedSdk latest = StreamEx.of(sdks).findFirst().orElse(null);
    if (latest != null) {
      return Collections.singleton(latest.getHomePath());
    }
    return Collections.emptyList();
  }

  @Override
  public boolean isValidSdkHome(final @NotNull String path) {
    return PythonSdkFlavor.getFlavor(path) != null;
  }

  @Override
  public @NotNull FileChooserDescriptor getHomeChooserDescriptor() {
    final var descriptor = new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public void validateSelectedFiles(VirtualFile @NotNull [] files) throws Exception {
        if (files.length != 0) {
          VirtualFile file = files[0];
          if (!isLocatedInWsl(file) && !isValidSdkHome(file.getPath())) {
            throw new Exception(PyBundle.message("python.sdk.error.invalid.interpreter.selected", file.getName()));
          }
        }
      }

      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        // TODO: add a better, customizable filtering
        if (!file.isDirectory()) {
          if (isLocatedInLocalWindowsFS(file)) {
            String path = file.getPath();
            boolean looksExecutable = false;
            for (String ext : PythonSdkUtil.WINDOWS_EXECUTABLE_SUFFIXES) {
              if (path.endsWith(ext)) {
                looksExecutable = true;
                break;
              }
            }
            return looksExecutable && super.isFileVisible(file, showHiddenFiles);
          }
        }
        return super.isFileVisible(file, showHiddenFiles);
      }
    }.withTitle(PyBundle.message("sdk.select.path")).withShowHiddenFiles(SystemInfo.isUnix);

    // XXX: Workaround for PY-21787 and PY-43507 since the native macOS dialog always follows symlinks
    if (SystemInfo.isMac) {
      descriptor.setForcedToUseIdeaFileChooser(true);
    }

    return descriptor;
  }

  private static boolean isLocatedInLocalWindowsFS(@NotNull VirtualFile file) {
    return SystemInfo.isWindows && !isCustomPythonSdkHomePath(file.getPath());
  }

  private static boolean isLocatedInWsl(@NotNull VirtualFile file) {
    return SystemInfo.isWindows && isCustomPythonSdkHomePath(file.getPath());
  }

  @Override
  public boolean supportsCustomCreateUI() {
    return true;
  }

  @Override
  public void showCustomCreateUI(@NotNull SdkModel sdkModel,
                                 @NotNull JComponent parentComponent,
                                 @Nullable Sdk selectedSdk,
                                 @NotNull Consumer<? super Sdk> sdkCreatedCallback) {
    Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent));
    PyAddSdkDialog.show(project, null, Arrays.asList(sdkModel.getSdks()), sdk -> {
      if (sdk != null) {
        sdk.putUserData(SDK_CREATOR_COMPONENT_KEY, new WeakReference<>(parentComponent));
        sdkCreatedCallback.consume(sdk);
      }
    });
  }

  /**
   * Alters PATH so that a virtualenv is activated, if present.
   *
   * @param commandLine what to patch
   * @param sdk         SDK we're using
   */
  public static void patchCommandLineForVirtualenv(@NotNull GeneralCommandLine commandLine,
                                                   @NotNull Sdk sdk) {
    patchEnvironmentVariablesForVirtualenv(commandLine.getEnvironment(), sdk);
  }

  /**
   * Alters PATH so that a virtualenv is activated, if present.
   *
   * @param environment the environment to patch
   * @param sdk         SDK we're using
   */
  public static void patchEnvironmentVariablesForVirtualenv(@NotNull Map<String, String> environment,
                                                            @NotNull Sdk sdk) {
    final Map<String, String> virtualEnv = PySdkUtil.activateVirtualEnv(sdk);
    if (!virtualEnv.isEmpty()) {
      for (Map.Entry<String, String> entry : virtualEnv.entrySet()) {
        final String key = entry.getKey();
        final String value = entry.getValue();

        if (environment.containsKey(key)) {
          if (key.equalsIgnoreCase(PySdkUtil.PATH_ENV_VARIABLE)) {
            PythonEnvUtil.addToPathEnvVar(environment.get(key), value, false);
          }
        }
        else {
          environment.put(key, value);
        }
      }
    }
  }

  @Override
  public @NotNull String suggestSdkName(final @Nullable String currentSdkName, final @NotNull String sdkHome) {
    final String name = StringUtil.notNullize(suggestBaseSdkName(sdkHome), "Unknown");
    final File virtualEnvRoot = PythonSdkUtil.getVirtualEnvRoot(sdkHome);
    if (virtualEnvRoot != null) {
      final String path = FileUtil.getLocationRelativeToUserHome(virtualEnvRoot.getAbsolutePath());
      return name + " virtualenv at " + path;
    }
    else {
      return name;
    }
  }

  public static @Nullable String suggestBaseSdkName(@NotNull String sdkHome) {
    final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdkHome);
    if (flavor == null) return null;
    return flavor.getName() + " " + flavor.getLanguageLevel(sdkHome);
  }

  @Override
  public @Nullable AdditionalDataConfigurable createAdditionalDataConfigurable(final @NotNull SdkModel sdkModel,
                                                                               final @NotNull SdkModificator sdkModificator) {
    return null;
  }

  @Override
  public void saveAdditionalData(final @NotNull SdkAdditionalData additionalData, final @NotNull Element additional) {
    if (additionalData instanceof PythonSdkAdditionalData) {
      ((PythonSdkAdditionalData)additionalData).save(additional);
    }
  }

  @Override
  public SdkAdditionalData loadAdditionalData(final @NotNull Sdk currentSdk, final @NotNull Element additional) {
    String homePath = currentSdk.getHomePath();

    if (homePath != null) {

      // We decided to get rid of this prefix
      if (homePath.startsWith(LEGACY_TARGET_PREFIX)) {
        ((SdkModificator)currentSdk).setHomePath(homePath.substring(LEGACY_TARGET_PREFIX.length()));
      }

      if (additional.getAttributeBooleanValue(PyDetectedSdkAdditionalData.PY_DETECTED_SDK_MARKER)) {
        PyDetectedSdkAdditionalData data = new PyDetectedSdkAdditionalData(null, null);
        data.load(additional);
        TargetEnvironmentConfiguration targetEnvironmentConfiguration = loadTargetConfiguration(additional);
        if (targetEnvironmentConfiguration != null) {
          data.setTargetEnvironmentConfiguration(targetEnvironmentConfiguration);
        }
        return data;
      }

      var targetAdditionalData = PyTargetAwareAdditionalData.loadTargetAwareData(currentSdk, additional);
      if (targetAdditionalData != null) {
        return targetAdditionalData;
      }
      else if (isCustomPythonSdkHomePath(homePath)) {
        PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
        if (manager != null) {
          return manager.loadRemoteSdkData(currentSdk, additional);
        }
        // TODO we should have "remote" SDK data with unknown credentials anyway!
      }
    }
    var additionalData = PySdkProvider.EP_NAME.getExtensionList().stream()
      .map(ext -> ext.loadAdditionalDataForSdk(additional))
      .filter(data -> data != null)
      .findFirst()
      .orElseGet(() -> PythonSdkAdditionalData.loadFromElement(additional));
    // Convert legacy conda SDK, temporary fix.
    PyCondaSdkFixKt.fixPythonCondaSdk(currentSdk, additionalData);
    return additionalData;
  }

  /**
   * Returns whether provided Python interpreter path corresponds to custom
   * Python SDK.
   *
   * @param homePath SDK home path
   * @return whether provided Python interpreter path corresponds to custom Python SDK
   */
  @Contract(pure = true)
  static boolean isCustomPythonSdkHomePath(@NotNull String homePath) {
    return PythonSdkUtil.isCustomPythonSdkHomePath(homePath);
  }

  public static boolean isSkeletonsPath(String path) {
    return path.contains(PythonSdkUtil.SKELETON_DIR_NAME);
  }

  @Override
  public @NotNull @NonNls String getPresentableName() {
    return "Python SDK";
  }

  @Override
  public @NotNull String sdkPath(@NotNull VirtualFile homePath) {
    String path = super.sdkPath(homePath);
    PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(path);
    if (flavor != null) {
      VirtualFile sdkPath = flavor.getSdkPath(homePath);
      if (sdkPath != null) {
        return FileUtil.toSystemDependentName(sdkPath.getPath());
      }
    }
    return FileUtil.toSystemDependentName(path);
  }

  @Override
  public void setupSdkPaths(@NotNull Sdk sdk) {
    if (PlatformUtils.isFleetBackend() || PlatformUtils.isQodana()) return;
    final WeakReference<Component> ownerComponentRef = sdk.getUserData(SDK_CREATOR_COMPONENT_KEY);
    final Component ownerComponent = SoftReference.dereference(ownerComponentRef);
    AtomicReference<Project> projectRef = new AtomicReference<>();
    ApplicationManager.getApplication().invokeAndWait(() -> {
      if (ownerComponent != null) {
        projectRef.set(CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(ownerComponent)));
      }
      else {
        projectRef.set(CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext()));
      }
    });
    PythonSdkUpdater.updateOrShowError(sdk, projectRef.get(), ownerComponent);
  }

  @Override
  public boolean setupSdkPaths(@NotNull Sdk sdk, @NotNull SdkModel sdkModel) {
    return true;  // run setupSdkPaths only once (from PythonSdkDetailsStep). Skip this from showCustomCreateUI
  }

  public static void notifyRemoteSdkSkeletonsFail(final InvalidSdkException e, final @Nullable Runnable restartAction) {
    NotificationListener notificationListener;
    String notificationMessage;
    if (e.getCause() instanceof VagrantNotStartedException) {
      notificationListener =
        (notification, event) -> {
          final PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
          if (manager != null) {
            try {
              VagrantNotStartedException cause = (VagrantNotStartedException)e.getCause();
              manager.runVagrant(cause.getVagrantFolder(), cause.getMachineName());
            }
            catch (ExecutionException e1) {
              throw new RuntimeException(e1);
            }
          }
          if (restartAction != null) {
            restartAction.run();
          }
        };
      notificationMessage = new HtmlBuilder()
        .append(e.getMessage())
        .appendLink("#", PyBundle.message("python.vagrant.refresh.skeletons"))
        .toString();
    }
    else if (ExceptionUtil.causedBy(e, ExceptionFix.class)) {
      final ExceptionFix fix = ExceptionUtil.findCause(e, ExceptionFix.class);
      notificationListener =
        (notification, event) -> {
          fix.apply();
          if (restartAction != null) {
            restartAction.run();
          }
        };
      notificationMessage = fix.getNotificationMessage(e.getMessage());
    }
    else {
      notificationListener = null;
      notificationMessage = e.getMessage();
    }

    Notification notification =
      new Notification("Python SDK Updater", PyBundle.message("sdk.gen.failed.notification.title"), notificationMessage,
                       NotificationType.WARNING);
    if (notificationListener != null) notification.setListener(notificationListener);
    notification.notify(null);
  }

  public static @NotNull VirtualFile getSdkRootVirtualFile(@NotNull VirtualFile path) {
    String suffix = path.getExtension();
    if (suffix != null) {
      suffix = StringUtil.toLowerCase(suffix); // Why on earth empty suffix is null and not ""?
    }
    if (!path.isDirectory() && ("zip".equals(suffix) || "egg".equals(suffix))) {
      // a .zip / .egg file must have its root extracted first
      final VirtualFile jar = JarFileSystem.getInstance().getJarRootForLocalFile(path);
      if (jar != null) {
        return jar;
      }
    }
    return path;
  }

  @Override
  public String getVersionString(@NotNull Sdk sdk) {
    SdkAdditionalData sdkAdditionalData = sdk.getSdkAdditionalData();
    if (sdkAdditionalData instanceof PyTargetAwareAdditionalData) {
      // TODO [targets] Cache version as for `PyRemoteSdkAdditionalDataBase`
      String versionString;
      try {
        versionString = PyInterpreterVersionUtil.getInterpreterVersion((PyTargetAwareAdditionalData)sdkAdditionalData, null, true);
      }
      catch (Exception e) {
        versionString = "undefined";
      }
      return versionString;
    }
    else if (sdkAdditionalData instanceof PyRemoteSdkAdditionalDataBase data) {
      assert data != null;
      String versionString = data.getVersionString();
      if (StringUtil.isEmpty(versionString)) {
        try {
          versionString =
            PyRemoteInterpreterUtil.getInterpreterVersion(null, data, true);
        }
        catch (Exception e) {
          LOG.warn("Couldn't get interpreter version:" + e.getMessage(), e);
          versionString = "undefined";
        }
        data.setVersionString(versionString);
      }
      return versionString;
    }
    else {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        final var version = sdk.getUserData(MOCK_PY_VERSION_KEY);
        if (version != null) {
          return version;
        }
      }

      String homePath = sdk.getHomePath();
      return homePath == null ? null : getVersionString(homePath);
    }
  }

  @Override
  public @Nullable String getVersionString(final @NotNull String sdkHome) {
    // Paths like \\wsl and ssh:// can't be used here
    if (isCustomPythonSdkHomePath(sdkHome)) {
      return null;
    }
    final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdkHome);
    return flavor != null ? flavor.getVersionString(sdkHome) : null;
  }

  @Override
  public boolean isRootTypeApplicable(final @NotNull OrderRootType type) {
    return type == OrderRootType.CLASSES;
  }

  @Override
  public boolean sdkHasValidPath(@NotNull Sdk sdk) {
    if (PythonSdkUtil.isRemote(sdk)) {
      return true;
    }
    VirtualFile homeDir = sdk.getHomeDirectory();
    return homeDir != null && homeDir.isValid();
  }

  public static boolean isIncompleteRemote(@NotNull Sdk sdk) {
    if (sdk.getSdkAdditionalData() instanceof PyRemoteSdkAdditionalDataBase) {
      if (!((PyRemoteSdkAdditionalDataBase)sdk.getSdkAdditionalData()).isValid()) {
        return true;
      }
    }
    return false;
  }

  public static boolean isRunAsRootViaSudo(@NotNull Sdk sdk) {
    SdkAdditionalData data = sdk.getSdkAdditionalData();
    return data instanceof PyRemoteSdkAdditionalDataBase pyRemoteSdkAdditionalData && pyRemoteSdkAdditionalData.isRunAsRootViaSudo() ||
           data instanceof PyTargetAwareAdditionalData pyTargetAwareAdditionalData && pyTargetAwareAdditionalData.isRunAsRootViaSudo();
  }

  public static boolean hasInvalidRemoteCredentials(@NotNull Sdk sdk) {
    if (sdk.getSdkAdditionalData() instanceof PyRemoteSdkAdditionalDataBase) {
      final Ref<Boolean> result = Ref.create(false);
      ((PyRemoteSdkAdditionalDataBase)sdk.getSdkAdditionalData()).switchOnConnectionType(
        new LanguageCaseCollector<PyCredentialsContribution>() {

          @Override
          protected void processLanguageContribution(PyCredentialsContribution languageContribution, Object credentials) {
            result.set(!languageContribution.isValid(credentials));
          }
        }.collectCases(PyCredentialsContribution.class));
      return result.get();
    }
    return false;
  }

  public static @NotNull String getSdkKey(@NotNull Sdk sdk) {
    return sdk.getName();
  }


  @Override
  public boolean isLocalSdk(@NotNull Sdk sdk) {
    return !PythonSdkUtil.isRemote(sdk);
  }

  public static @Nullable Sdk findLocalCPython(@Nullable Module module) {
    final Sdk moduleSDK = PythonSdkUtil.findPythonSdk(module);
    return findLocalCPythonForSdk(moduleSDK);
  }

  public static @Nullable Sdk findLocalCPythonForSdk(@Nullable Sdk existingSdk) {
    if (existingSdk != null && !PythonSdkUtil.isRemote(existingSdk) && PythonSdkFlavor.getFlavor(existingSdk) instanceof CPythonSdkFlavor) {
      return existingSdk;
    }
    for (Sdk sdk : ContainerUtil.sorted(PythonSdkUtil.getAllSdks(), PreferredSdkComparator.INSTANCE)) {
      if (!PythonSdkUtil.isRemote(sdk)) {
        return sdk;
      }
    }
    return null;
  }

  /**
   * @deprecated use {@link PySdkUtil#getLanguageLevelForSdk(com.intellij.openapi.projectRoots.Sdk)} instead
   */
  @Deprecated(forRemoval = true)
  public static @NotNull LanguageLevel getLanguageLevelForSdk(@Nullable Sdk sdk) {
    return PySdkUtil.getLanguageLevelForSdk(sdk);
  }

  public static @Nullable Sdk findPython2Sdk(@Nullable Module module) {
    final Sdk moduleSDK = PythonSdkUtil.findPythonSdk(module);
    if (moduleSDK != null && getLanguageLevelForSdk(moduleSDK).isPython2()) {
      return moduleSDK;
    }
    return findPython2Sdk(PythonSdkUtil.getAllSdks());
  }

  public static @Nullable Sdk findPython2Sdk(@NotNull List<? extends Sdk> sdks) {
    for (Sdk sdk : ContainerUtil.sorted(sdks, PreferredSdkComparator.INSTANCE)) {
      if (getLanguageLevelForSdk(sdk).isPython2()) {
        return sdk;
      }
    }
    return null;
  }

  @Override
  public boolean allowWslSdkForLocalProject() {
    return true;
  }

  /**
   * @return if SDK is mock (used by tests only)
   */
  @SuppressWarnings("TestOnlyProblems")
  public static boolean isMock(@NotNull Sdk sdk) {
    return (sdk.getUserData(MOCK_PY_VERSION_KEY) != null) ||
           (sdk.getUserData(MOCK_SYS_PATH_KEY) != null) ||
           (sdk.getUserData(MOCK_PY_MARKER_KEY) != null);
  }

  /**
   * Returns mocked path (stored in sdk with {@link #MOCK_SYS_PATH_KEY} in test)
   */
  public static @NotNull List<String> getMockPath(@NotNull Sdk sdk) {
    var workDir = Paths.get(Objects.requireNonNull(sdk.getHomePath())).getParent().toString();
    var mockPaths = sdk.getUserData(MOCK_SYS_PATH_KEY);
    return mockPaths != null ? Collections.unmodifiableList(mockPaths) : Collections.singletonList(workDir);
  }
}
