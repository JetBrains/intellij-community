// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.ide.DataManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.reference.SoftReference;
import com.intellij.remote.ExceptionFix;
import com.intellij.remote.VagrantNotStartedException;
import com.intellij.remote.ext.LanguageCaseCollector;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonHelper;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.remote.PyCredentialsContribution;
import com.jetbrains.python.remote.PyRemoteInterpreterUtil;
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
import com.jetbrains.python.sdk.add.PyAddSdkDialog;
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor;
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.sdk.pipenv.PyPipEnvSdkAdditionalData;
import icons.PythonIcons;
import one.util.streamex.StreamEx;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Class should be final and singleton since some code checks its instance by ref.
 *
 * @author yole
 */
public final class PythonSdkType extends SdkType {
  private static final Logger LOG = Logger.getInstance(PythonSdkType.class);

  private static final int MINUTE = 60 * 1000; // 60 seconds, used with script timeouts
  @NonNls private static final String SKELETONS_TOPIC = "Skeletons";

  private static final Key<WeakReference<Component>> SDK_CREATOR_COMPONENT_KEY = Key.create("#com.jetbrains.python.sdk.creatorComponent");

  /**
   * Note that <i>\w+.*</i> pattern is not sufficient because we need also the
   * hyphen sign (<i>-</i>) for <i>docker-compose:</i> scheme.
   */
  private static final Pattern CUSTOM_PYTHON_SDK_HOME_PATH_PATTERN = Pattern.compile("[-a-zA-Z_0-9]{2,}:.*");

  public static PythonSdkType getInstance() {
    return SdkType.findInstance(PythonSdkType.class);
  }

  private PythonSdkType() {
    super(PyNames.PYTHON_SDK_ID_NAME); //don't change this call as the string used for comparison
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.Python;
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return "reference.project.structure.sdk.python";
  }

  @Override
  @NotNull
  public Icon getIconForAddAction() {
    return PythonFileType.INSTANCE.getIcon();
  }

  /**
   * @return name of builtins skeleton file; for Python 2.x it is '{@code __builtins__.py}'.
   */
  @NotNull
  @NonNls
  public static String getBuiltinsFileName(@NotNull Sdk sdk) {
    return PyBuiltinCache.getBuiltinsFileName(getLanguageLevelForSdk(sdk));
  }

  @Override
  @NonNls
  @Nullable
  public String suggestHomePath() {
    return null;
  }

  @NotNull
  @Override
  public Collection<String> suggestHomePaths() {
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
  public boolean isValidSdkHome(@Nullable final String path) {
    return PythonSdkFlavor.getFlavor(path) != null;
  }

  @NotNull
  @Override
  public FileChooserDescriptor getHomeChooserDescriptor() {
    final boolean isWindows = SystemInfo.isWindows;
    return new FileChooserDescriptor(true, false, false, false, false, false) {
      @Override
      public void validateSelectedFiles(VirtualFile @NotNull [] files) throws Exception {
        if (files.length != 0) {
          if (!isValidSdkHome(files[0].getPath())) {
            throw new Exception(PyBundle.message("sdk.error.invalid.interpreter.name.$0", files[0].getName()));
          }
        }
      }

      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        // TODO: add a better, customizable filtering
        if (!file.isDirectory()) {
          if (isWindows) {
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
  }

  @Override
  public boolean supportsCustomCreateUI() {
    return true;
  }

  @Override
  public void showCustomCreateUI(@NotNull SdkModel sdkModel,
                                 @NotNull final JComponent parentComponent,
                                 @NotNull final Consumer<Sdk> sdkCreatedCallback) {
    Project project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(parentComponent));
    PyAddSdkDialog.show(project, null, Arrays.asList(sdkModel.getSdks()), sdk -> {
      if (sdk != null) {
        sdk.putUserData(SDK_CREATOR_COMPONENT_KEY, new WeakReference<>(parentComponent));
        sdkCreatedCallback.consume(sdk);
      }
    });
  }

  @Nullable
  public Sdk getVirtualEnvBaseSdk(Sdk sdk) {
    if (PythonSdkUtil.isVirtualEnv(sdk)) {
      final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdk);
      final String version = getVersionString(sdk);
      if (flavor != null && version != null) {
        for (Sdk baseSdk : getAllSdks()) {
          if (!PythonSdkUtil.isRemote(baseSdk)) {
            final PythonSdkFlavor baseFlavor = PythonSdkFlavor.getFlavor(baseSdk);
            if (!PythonSdkUtil.isVirtualEnv(baseSdk) && flavor.equals(baseFlavor) && version.equals(getVersionString(baseSdk))) {
              return baseSdk;
            }
          }
        }
      }
    }
    return null;
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
    final Map<String, String> virtualEnv = activateVirtualEnv(sdk);
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

  @NotNull
  @Override
  public String suggestSdkName(@Nullable final String currentSdkName, final String sdkHome) {
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

  @Nullable
  public static String suggestBaseSdkName(@NotNull String sdkHome) {
    final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdkHome);
    if (flavor == null) return null;
    return flavor.getName() + " " + flavor.getLanguageLevel(sdkHome);
  }

  @Override
  @Nullable
  public AdditionalDataConfigurable createAdditionalDataConfigurable(@NotNull final SdkModel sdkModel,
                                                                     @NotNull final SdkModificator sdkModificator) {
    return null;
  }

  @Override
  public void saveAdditionalData(@NotNull final SdkAdditionalData additionalData, @NotNull final Element additional) {
    if (additionalData instanceof PythonSdkAdditionalData) {
      ((PythonSdkAdditionalData)additionalData).save(additional);
    }
  }

  @Override
  public SdkAdditionalData loadAdditionalData(@NotNull final Sdk currentSdk, @NotNull final Element additional) {
    String homePath = currentSdk.getHomePath();
    if (homePath != null && isCustomPythonSdkHomePath(homePath)) {
      PythonRemoteInterpreterManager manager = PythonRemoteInterpreterManager.getInstance();
      if (manager != null) {
        return manager.loadRemoteSdkData(currentSdk, additional);
      }
      // TODO we should have "remote" SDK data with unknown credentials anyway!
    }
    // TODO: Extract loading additional SDK data into a Python SDK provider
    final PyPipEnvSdkAdditionalData pipEnvData = PyPipEnvSdkAdditionalData.load(additional);
    if (pipEnvData != null) {
      return pipEnvData;
    }
    return PythonSdkAdditionalData.load(currentSdk, additional);
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
    return CUSTOM_PYTHON_SDK_HOME_PATH_PATTERN.matcher(homePath).matches();
  }

  public static boolean isSkeletonsPath(String path) {
    return path.contains(PythonSdkUtil.SKELETON_DIR_NAME);
  }

  @Override
  @NotNull
  @NonNls
  public String getPresentableName() {
    return "Python SDK";
  }

  @NotNull
  @Override
  public String sdkPath(@NotNull VirtualFile homePath) {
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
    final Project project;
    final WeakReference<Component> ownerComponentRef = sdk.getUserData(SDK_CREATOR_COMPONENT_KEY);
    final Component ownerComponent = SoftReference.dereference(ownerComponentRef);
    if (ownerComponent != null) {
      project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext(ownerComponent));
    }
    else {
      project = CommonDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    }
    PythonSdkUpdater.updateOrShowError(sdk, null, project, ownerComponent);
  }

  @Override
  public boolean setupSdkPaths(@NotNull Sdk sdk, @NotNull SdkModel sdkModel) {
    return true;  // run setupSdkPaths only once (from PythonSdkDetailsStep). Skip this from showCustomCreateUI
  }

  public static void notifyRemoteSdkSkeletonsFail(final InvalidSdkException e, @Nullable final Runnable restartAction) {
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
      @NonNls
      final String before = "\n<a href=\"#\">";
      @NonNls
      final String after = "</a>";
      notificationMessage = e.getMessage() + before + PyBundle.message("python.vagrant.refresh.skeletons") + after;
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

    Notifications.Bus.notify(
      new Notification(
        SKELETONS_TOPIC, PyBundle.message("sdk.gen.failed.notification.title"),
        notificationMessage,
        NotificationType.WARNING,
        notificationListener
      )
    );
  }

  @NotNull
  public static VirtualFile getSdkRootVirtualFile(@NotNull VirtualFile path) {
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

  /**
   * Returns skeletons location on the local machine. Independent of SDK credentials type (e.g. ssh, Vagrant, Docker or else).
   */
  @NotNull
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static String getSkeletonsPath(String basePath, String sdkHome) {
    return PythonSdkUtil.getSkeletonsPath(basePath, sdkHome);
  }

  @NotNull
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static String getSkeletonsRootPath(String basePath) {
    return PythonSdkUtil.getSkeletonsRootPath(basePath);
  }

  @NotNull
  public static List<String> getSysPath(@NotNull Sdk sdk) throws InvalidSdkException {
    String working_dir = new File(sdk.getHomePath()).getParent();
    Application application = ApplicationManager.getApplication();
    if (application != null && (!application.isUnitTestMode() || ApplicationInfoImpl.isInStressTest())) {
      return getSysPathsFromScript(sdk);
    }
    else { // mock sdk
      List<String> ret = new ArrayList<>(1);
      ret.add(working_dir);
      return ret;
    }
  }

  @NotNull
  public static List<String> getSysPathsFromScript(@NotNull Sdk sdk) throws InvalidSdkException {
    // to handle the situation when PYTHONPATH contains ., we need to run the syspath script in the
    // directory of the script itself - otherwise the dir in which we run the script (e.g. /usr/bin) will be added to SDK path
    final String binaryPath = sdk.getHomePath();
    GeneralCommandLine cmd = PythonHelper.SYSPATH.newCommandLine(binaryPath, new ArrayList<String>());
    final ProcessOutput runResult = PySdkUtil.getProcessOutput(cmd, new File(binaryPath).getParent(),
                                                               activateVirtualEnv(sdk), MINUTE);
    if (!runResult.checkSuccess(LOG)) {
      throw new InvalidSdkException(String.format("Failed to determine Python's sys.path value:\nSTDOUT: %s\nSTDERR: %s",
                                                  runResult.getStdout(),
                                                  runResult.getStderr()));
    }
    return runResult.getStdoutLines();
  }

  @Nullable
  @Override
  public String getVersionString(@NotNull Sdk sdk) {
    if (PythonSdkUtil.isRemote(sdk)) {
      final PyRemoteSdkAdditionalDataBase data = (PyRemoteSdkAdditionalDataBase)sdk.getSdkAdditionalData();
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
      return getVersionString(sdk.getHomePath());
    }
  }

  @Override
  @Nullable
  public String getVersionString(@Nullable final String sdkHome) {
    final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdkHome);
    return flavor != null ? flavor.getVersionString(sdkHome) : null;
  }

  @Override
  public boolean isRootTypeApplicable(@NotNull final OrderRootType type) {
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

  public static boolean isIncompleteRemote(Sdk sdk) {
    if (PythonSdkUtil.isRemote(sdk)) {
      //noinspection ConstantConditions
      if (!((PyRemoteSdkAdditionalDataBase)sdk.getSdkAdditionalData()).isValid()) {
        return true;
      }
    }
    return false;
  }

  public static boolean isRunAsRootViaSudo(@NotNull Sdk sdk) {
    SdkAdditionalData data = sdk.getSdkAdditionalData();
    return data instanceof PyRemoteSdkAdditionalDataBase && ((PyRemoteSdkAdditionalDataBase)data).isRunAsRootViaSudo();
  }

  public static boolean hasInvalidRemoteCredentials(Sdk sdk) {
    if (PythonSdkUtil.isRemote(sdk)) {
      final Ref<Boolean> result = Ref.create(false);
      //noinspection ConstantConditions
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

  @Deprecated
  @Nullable
  public static Sdk getSdk(@NotNull final PsiElement element) {
    return PythonSdkUtil.findPythonSdk(element);
  }

  @NotNull
  public static String getSdkKey(@NotNull Sdk sdk) {
    return sdk.getName();
  }


  @Override
  public boolean isLocalSdk(@NotNull Sdk sdk) {
    return !PythonSdkUtil.isRemote(sdk);
  }

  @NotNull
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static Map<String, String> activateVirtualEnv(@NotNull Sdk sdk) {
    return PySdkUtil.activateVirtualEnv(sdk);
  }

  @NotNull
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static Map<String, String> activateVirtualEnv(@NotNull String sdkHome) {
    return PySdkUtil.activateVirtualEnv(sdkHome);
  }

  @Nullable
  public static Sdk findLocalCPython(@Nullable Module module) {
    final Sdk moduleSDK = PythonSdkUtil.findPythonSdk(module);
    if (moduleSDK != null && !PythonSdkUtil.isRemote(moduleSDK) && PythonSdkFlavor.getFlavor(moduleSDK) instanceof CPythonSdkFlavor) {
      return moduleSDK;
    }
    for (Sdk sdk : ContainerUtil.sorted(getAllSdks(), PreferredSdkComparator.INSTANCE)) {
      if (!PythonSdkUtil.isRemote(sdk)) {
        return sdk;
      }
    }
    return null;
  }

  @NotNull
  public static LanguageLevel getLanguageLevelForSdk(@Nullable Sdk sdk) {
    if (sdk != null && PythonSdkUtil.isPythonSdk(sdk)) {
      final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdk);
      if (flavor != null) {
        return flavor.getLanguageLevel(sdk);
      }
    }
    return LanguageLevel.getDefault();
  }

  @Nullable
  public static Sdk findPython2Sdk(@Nullable Module module) {
    final Sdk moduleSDK = PythonSdkUtil.findPythonSdk(module);
    if (moduleSDK != null && getLanguageLevelForSdk(moduleSDK).isPython2()) {
      return moduleSDK;
    }
    return findPython2Sdk(getAllSdks());
  }

  @Nullable
  public static Sdk findPython2Sdk(@NotNull List<? extends Sdk> sdks) {
    for (Sdk sdk : ContainerUtil.sorted(sdks, PreferredSdkComparator.INSTANCE)) {
      if (getLanguageLevelForSdk(sdk).isPython2()) {
        return sdk;
      }
    }
    return null;
  }


  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static boolean hasValidSdk() {
    return PythonSdkUtil.hasValidSdk();
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static boolean isInvalid(@NotNull Sdk sdk) {
    return PythonSdkUtil.isInvalid(sdk);
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static boolean isRemote(@Nullable Sdk sdk) {
    return PythonSdkUtil.isRemote(sdk);
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static boolean isRemote(@Nullable String sdkPath) {
    return PythonSdkUtil.isRemote(sdkPath);
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static boolean isVirtualEnv(@NotNull Sdk sdk) {
    return PythonSdkUtil.isVirtualEnv(sdk);
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static boolean isVirtualEnv(@Nullable String path) {
    return PythonSdkUtil.isVirtualEnv(path);
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static boolean isConda(@NotNull Sdk sdk) {
    return PythonSdkUtil.isConda(sdk);
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static boolean isConda(@Nullable String sdkPath) {
    return PythonSdkUtil.isConda(sdkPath);
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static boolean isCondaVirtualEnv(@NotNull Sdk sdk) {
    return PythonSdkUtil.isCondaVirtualEnv(sdk);
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  @Nullable
  public static File getVirtualEnvRoot(@NotNull final String binaryPath) {
    return PythonSdkUtil.getVirtualEnvRoot(binaryPath);
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  @Nullable
  public static File findExecutableFile(File parent, String name) {
    return PythonSdkUtil.findExecutableFile(parent, name);
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static final OrderRootType BUILTIN_ROOT_TYPE = PythonSdkUtil.BUILTIN_ROOT_TYPE;

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static List<Sdk> getAllSdks() {
    return PythonSdkUtil.getAllSdks();
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  @Nullable
  public static Sdk findPythonSdk(@Nullable Module module) {
    return PythonSdkUtil.findPythonSdk(module);
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  @Nullable
  public static Sdk findPythonSdk(@NotNull final PsiElement element) {
    return PythonSdkUtil.findPythonSdk(element);
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  @Nullable
  public static Sdk findSdkByPath(@Nullable String path) {
    return PythonSdkUtil.findSdkByPath(path);
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  @Nullable
  public static Sdk findSdkByPath(List<? extends Sdk> sdkList, @Nullable String path) {
    return PythonSdkUtil.findSdkByPath(sdkList, path);
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static boolean isStdLib(@NotNull VirtualFile vFile, @Nullable Sdk pythonSdk) {
    return PythonSdkUtil.isStdLib(vFile, pythonSdk);
  }


  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  @Nullable
  public static VirtualFile getSitePackagesDirectory(@NotNull Sdk pythonSdk) {
    return PythonSdkUtil.getSitePackagesDirectory(pythonSdk);
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  public static List<Sdk> getAllLocalCPythons() {
    return PythonSdkUtil.getAllLocalCPythons();
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  @Nullable
  public static String getPythonExecutable(@NotNull String rootPath) {
    return PythonSdkUtil.getPythonExecutable(rootPath);
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  @Nullable
  public static String getExecutablePath(@NotNull final String homeDirectory, @NotNull String name) {
    return PythonSdkUtil.getExecutablePath(homeDirectory, name);
  }

  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.3")
  @Nullable
  public static Sdk findSdkByKey(@NotNull String key) {
    return PythonSdkUtil.findSdkByKey(key);
  }
}

