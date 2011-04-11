package com.jetbrains.python.sdk;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetManager;
import com.intellij.ide.DataManager;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.CharFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.facet.PythonFacetSettings;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyQualifiedName;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.psi.PyUtil.sure;
import static com.jetbrains.python.sdk.PythonSdkType.SkeletonVersionChecker.versionFromString;

/**
 * @author yole
 */
public class PythonSdkType extends SdkType {
  private static final Logger LOG = Logger.getInstance("#" + PythonSdkType.class.getName());
  private static final String[] WINDOWS_EXECUTABLE_SUFFIXES = new String[]{"cmd", "exe", "bat", "com"};

  static final int MINUTE = 60 * 1000; // 60 seconds, used with script timeouts
  private List<String> myCachedSysPath;

  public static PythonSdkType getInstance() {
    return SdkType.findInstance(PythonSdkType.class);
  }

  public PythonSdkType() {
    super("Python SDK");
  }

  protected PythonSdkType(@NonNls String name) {
    super(name);
  }

  public Icon getIcon() {
    return PythonFileType.INSTANCE.getIcon();
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return "reference.project.structure.sdk.python";
  }

  public Icon getIconForAddAction() {
    return PythonFileType.INSTANCE.getIcon();
  }

  /**
   * Name of directory where skeleton files (despite the value) are stored.
   */
  public static final String SKELETON_DIR_NAME = "python_stubs";

  /**
   * @return name of builtins skeleton file; for Python 2.x it is '{@code __builtins__.py}'.
   */
  @NotNull
  @NonNls
  public static String getBuiltinsFileName(Sdk sdk) {
    final String version = sdk.getVersionString(); 
    if (version != null && version.startsWith("Python 3")) {
      return PyBuiltinCache.BUILTIN_FILE_3K;
    }
    return PyBuiltinCache.BUILTIN_FILE;
  }

  @NonNls
  @Nullable
  public String suggestHomePath() {
    Collection<String> candidates = suggestHomePaths();
    if (candidates.size() > 0) {
      // return latest version
      String[] candidateArray = ArrayUtil.toStringArray(candidates);
      return candidateArray[candidateArray.length - 1];
    }
    return null;
  }

  @Override
  public Collection<String> suggestHomePaths() {
    TreeSet<String> candidates = new TreeSet<String>(new Comparator<String>() {
      public int compare(String o1, String o2) {
        return findDigits(o1).compareTo(findDigits(o2));
      }
    });
    for (PythonSdkFlavor flavor : PythonSdkFlavor.getApplicableFlavors()) {
      candidates.addAll(flavor.suggestHomePaths());
    }
    return candidates;
  }

  private static String findDigits(String s) {
    int pos = StringUtil.findFirst(s, new CharFilter() {
      public boolean accept(char ch) {
        return Character.isDigit(ch);
      }
    });
    if (pos >= 0) {
      return s.substring(pos);
    }
    return s;
  }

  public boolean isValidSdkHome(final String path) {
    return PythonSdkFlavor.getFlavor(path) != null;
  }

  @Override
  public FileChooserDescriptor getHomeChooserDescriptor() {
    final boolean is_windows = SystemInfo.isWindows;
    FileChooserDescriptor result = new FileChooserDescriptor(true, false, false, false, false, false) {
      public void validateSelectedFiles(VirtualFile[] files) throws Exception {
        if (files.length != 0){
          if (!isValidSdkHome(files[0].getPath())){
            throw new Exception(PyBundle.message("sdk.error.invalid.interpreter.name.$0", files[0].getName()));
          }
        }
      }

      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        // TODO: add a better, customizable filtering
        if (! file.isDirectory()) {
          if (is_windows) {
            String path = file.getPath();
            boolean looks_executable = false;
            for (String ext : WINDOWS_EXECUTABLE_SUFFIXES) {
              if (path.endsWith(ext)) {
                looks_executable = true;
                break;
              }
            }
            return looks_executable && super.isFileVisible(file, showHiddenFiles);
          }
        }
        return super.isFileVisible(file, showHiddenFiles);
      }
    };
    result.setTitle(PyBundle.message("sdk.select.path"));
    return result;
  }

  /**
   * @param binaryPath must point to a Python interpreter
   * @return if the surroundings look like a virtualenv installation, its root is returned (normally the grandparent of binaryPath).
   */
  @Nullable
  public static File getVirtualEnvRoot(@NotNull final String binaryPath) {
    // binaryPath should contain an 'activate' script, and root should have bin (with us) and include and lib.
    try {
      File parent = new File(binaryPath).getParentFile();
      sure(parent != null && "bin".equals(parent.getName()));
      File activate_script = new File(parent, "activate_this.py");
      sure(activate_script.exists());
      File activate_source = null;
      if (SystemInfo.isWindows || SystemInfo.isOS2) {
        for (String suffix : WINDOWS_EXECUTABLE_SUFFIXES) {
          File file = new File(parent, "activate"+suffix);
          if (file.exists()) {
            activate_source = file;
            break;
          }
        }
      }
      else if (SystemInfo.isUnix) {
        File file = new File(parent, "activate");
        if (file.exists()) {
          activate_source = file;
        }
      }
      sure(activate_source);
      // NOTE: maybe read activate_source and see if it handles "VIRTUAL_ENV"
      File root = parent.getParentFile();
      sure(new File(root, "lib").exists());
      sure(new File(root, "include").exists());
      return root;
    }
    catch (IncorrectOperationException ignore) {
      // did not succeed
    }
    return null;
  }

  /**
   * Alters PATH so that a virtualenv is activated, if present.
   * @param commandLine what to patch
   * @param sdkHome home of SDK we're using
   * @param passParentEnvs iff true, include system paths in PATH
   */
  public static void patchCommandLineForVirtualenv(GeneralCommandLine commandLine, String sdkHome, boolean passParentEnvs) {
    @NonNls final String PATH = "PATH";
    String path_value;
    File virtualenv_root = getVirtualEnvRoot(sdkHome);
    if (virtualenv_root != null) {
      // prepend virtualenv bin if it's not already on PATH
      String virtualenv_bin = new File(virtualenv_root, "bin").getPath();

      if (passParentEnvs) {
        // append to PATH
        path_value = System.getenv(PATH);
        path_value = PythonEnvUtil.appendToPathEnvVar(path_value, virtualenv_bin);
      }
      else path_value = virtualenv_bin;
      Map<String, String> new_env = PythonEnvUtil.cloneEnv(commandLine.getEnvParams()); // we need a copy lest we change config's map.
      String existing_path = new_env.get(PATH);
      if (existing_path == null || !existing_path.contains(virtualenv_bin)) {
        new_env.put(PATH, path_value);
        commandLine.setEnvParams(new_env);
      }
    }
  }


  // /home/joe/foo -> ~/foo
  protected static String shortenDirName(String path) {
    String home = System.getProperty("user.home");
    if (path.startsWith(home)) {
      return "~" + path.substring(home.length());
    }
    else {
      return path;
    }
  }

  public String suggestSdkName(final String currentSdkName, final String sdkHome) {
    String name = getVersionString(sdkHome);
    final String short_home_name = shortenDirName(sdkHome);
    if (name != null) {
      File virtualenv_root = getVirtualEnvRoot(sdkHome);
      if (virtualenv_root != null) {
        name += " virtualenv at " + shortenDirName(virtualenv_root.getAbsolutePath());
      }
      else {
        name += " (" + short_home_name + ")";
      }
    }
    else {
      name = "Unknown at " + short_home_name;
    } // last resort
    return name;
  }

  @Nullable
  public AdditionalDataConfigurable createAdditionalDataConfigurable(final SdkModel sdkModel, final SdkModificator sdkModificator) {
    return null;
  }

  public void saveAdditionalData(final SdkAdditionalData additionalData, final Element additional) {
    if (additionalData instanceof PythonSdkAdditionalData ) {
      ((PythonSdkAdditionalData)additionalData).save(additional);
    }
  }

  @Override
  public SdkAdditionalData loadAdditionalData(final Sdk currentSdk, final Element additional) {
    // try to upgrade from previous version(s)
    String sdk_path = currentSdk.getHomePath();
    if (sdk_path != null) {
      // in versions up to 94.239, path points to lib dir; later it points to the interpreter itself
      if (! isValidSdkHome(sdk_path)) {
        if (SystemInfo.isWindows) {
          switchPathToInterpreter(currentSdk, "python.exe", "jython.bat"); // can't be in the same dir, safe to try
        }
        else if (SystemInfo.isMac) {
          // NOTE: not sure about jython
          switchPathToInterpreter(currentSdk, "bin/python", "bin/jython", "jython"); // can't be in the same dir, safe to try
        }
        else if (SystemInfo.isUnix) {
          String sdk_name = currentSdk.getName().toLowerCase();
          if (sdk_name.contains("jython")) {
            // NOTE: can't distinguish different installations in /usr/bin
            switchPathToInterpreter(currentSdk, "jython", "/usr/bin/jython", "/usr/local/bin/jython");
          }
          else if (sdk_name.contains("python")) {
            String sdk_home = new File(sdk_path).getName().toLowerCase(); // usually /usr/blahblah/pythonX.Y
            String version = sdk_home.substring("python".length());
            switchPathToInterpreter(currentSdk, "python"+version, "/usr/bin/python"+version, "/usr/local/bin/python"+version);
          }
        }
      }
    }

    // Don't fix skeletons here, PythonSdkUpdater will take care of that (see PY-1226 - no progress will be displayed if skeletons
    // generation is invoked from here

    return PythonSdkAdditionalData.load(additional);
  }

  private boolean switchPathToInterpreter(Sdk currentSdk, String... variants) {
    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    File sdk_file = new File(currentSdk.getHomePath());
    final String sdk_name = currentSdk.getName();
    boolean success = false;
    for (String interpreter : variants) {
      File binary = interpreter.startsWith("/")? new File(interpreter) : new File(sdk_file, interpreter);
      if (binary.exists()) {
        if (currentSdk instanceof SdkModificator) {
          final SdkModificator sdk_as_modificator = (SdkModificator)currentSdk;
          sdk_as_modificator.setHomePath(binary.getPath());
          sdk_as_modificator.setName(suggestSdkName(currentSdk.getName(), binary.getAbsolutePath()));
          //setupSdkPaths(currentSdk);
          success = true;
          break;
        }
      }
    }
    if (!success) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          Messages.showWarningDialog(project,
            "Failed to convert Python SDK '" + sdk_name + "'\nplease delete and re-create it",
            "Converting Python SDK"
          );
        }
      }, ModalityState.NON_MODAL);
    }
    return success;
  }

  @Nullable
  public static String findSkeletonsPath(Sdk sdk) {
    final String[] urls = sdk.getRootProvider().getUrls(BUILTIN_ROOT_TYPE);
    for (String url : urls) {
      if (url.contains(SKELETON_DIR_NAME)) {
        return VfsUtil.urlToPath(url);
      }
    }
    return null;
  }

  @Nullable
  public static VirtualFile findSkeletonsDir(Sdk sdk) {
    final VirtualFile[] virtualFiles = sdk.getRootProvider().getFiles(BUILTIN_ROOT_TYPE);
    for (VirtualFile virtualFile : virtualFiles) {
      if (virtualFile.isValid() && virtualFile.getPath().contains(SKELETON_DIR_NAME)) {
        return virtualFile;
      }
    }
    return null;
  }

  @NonNls
  public String getPresentableName() {
    return "Python SDK";
  }

  public void setupSdkPaths(final Sdk sdk) {
    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    final SdkModificator sdkModificator = sdk.getSdkModificator();
    final boolean success = setupSdkPaths(project, sdk, sdkModificator);
    if (success) {
      sdkModificator.commitChanges();
    }
    else {
      Messages.showErrorDialog(
        project,
        PyBundle.message("MSG.cant.setup.sdk.$0", FileUtil.toSystemDependentName(sdk.getSdkModificator().getHomePath())),
        PyBundle.message("MSG.title.bad.sdk")
      );
    }
  }

  public static boolean setupSdkPaths(final Project project, final Sdk sdk, final SdkModificator sdkModificator) {
    final ProgressManager progman = ProgressManager.getInstance();
    final Ref<Boolean> success = new Ref<Boolean>();
    success.set(true);
    final Task.Modal setupTask = new Task.Modal(project, "Setting up library files for " + sdk.getName(), false) {
      // TODO: make this a backgroundable task. see #setupSdkPaths(final Sdk sdk) and its modificator handling
      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          sdkModificator.removeAllRoots();
          updateSdkRootsFromSysPath(sdkModificator, indicator, (PythonSdkType)sdk.getSdkType());
          if (!ApplicationManager.getApplication().isUnitTestMode()) {
            regenerateSkeletons(indicator, sdk, getSkeletonsPath(sdk.getHomePath()), null, success);
          }
          //sdkModificator.commitChanges() must happen outside, in dispatch thread.
        }
        catch (InvalidSdkException e) {
          success.set(false);
        }
      }
    };
    progman.run(setupTask);
    return success.get();
  }

  /**
   * In which root type built-in skeletons are put.
   */
  public static final OrderRootType BUILTIN_ROOT_TYPE = OrderRootType.CLASSES;

  private final static Pattern PYTHON_NN_RE = Pattern.compile("python\\d\\.\\d.*");

  public static void updateSdkRootsFromSysPath(SdkModificator sdkModificator, ProgressIndicator indicator, PythonSdkType sdkType) {
    Application application = ApplicationManager.getApplication();
    boolean not_in_unit_test_mode = (application != null && !application.isUnitTestMode());

    String bin_path = sdkModificator.getHomePath();
    assert bin_path != null;
    final String sep = File.separator;
    // we have a number of lib dirs, those listed in python's sys.path
    if (indicator != null) {
      indicator.setText("Adding library roots");
    }
    // Add folders from sys.path
    final List<String> paths = sdkType.getSysPath(bin_path);
    if ((paths != null) && paths.size() > 0) {
      // add every path as root.
      for (String path : paths) {
        if (!path.contains(sep)) continue; // TODO: interpret possible 'special' paths reasonably
        if (indicator != null) {
          indicator.setText2(path);
        }
        addSdkRoot(sdkModificator, path);
      }
      @NonNls final String stubs_path = getSkeletonsPath(bin_path);
      new File(stubs_path).mkdirs();      
      final VirtualFile builtins_root = LocalFileSystem.getInstance().refreshAndFindFileByPath(stubs_path);
      assert builtins_root != null;
      sdkModificator.addRoot(builtins_root, BUILTIN_ROOT_TYPE);
    }
    if (not_in_unit_test_mode) {
      File venv_root = getVirtualEnvRoot(bin_path);
      if (venv_root != null && venv_root.isDirectory()) {
        File lib_root = new File(venv_root, "lib");
        if (lib_root.isDirectory()) {
          String[] inside = lib_root.list();
          for (String s : inside) {
            if (PYTHON_NN_RE.matcher(s).matches()) {
              File py_lib_root = new File(lib_root, s);
              String[] flag_files = py_lib_root.list(new FilenameFilter() {
                @Override
                public boolean accept(File file, String s) {
                  return "no-global-site-packages.txt".equals(s);
                }
              });
              if (flag_files != null) return; // don't add hardcoded paths
            }
          }
        }
      }
      sdkType.addHardcodedPaths(sdkModificator);
    }
  }

  @SuppressWarnings({"MethodMayBeStatic"})
  protected void addHardcodedPaths(SdkModificator sdkModificator) {
    // Add python-django installed as package in Linux
    // NOTE: fragile and arbitrary
    if (SystemInfo.isLinux) {
      final VirtualFile file = LocalFileSystem.getInstance().findFileByPath("/usr/lib/python-django");
      if (file != null){
        sdkModificator.addRoot(file, OrderRootType.CLASSES);
      }
    }
  }

  public static void addSdkRoot(SdkModificator sdkModificator, String path) {
    VirtualFile child = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
    if (child != null) {
      @NonNls String suffix = child.getExtension();
      if (suffix != null) suffix = suffix.toLowerCase(); // Why on earth empty suffix is null and not ""?
      if ((!child.isDirectory()) && ("zip".equals(suffix) || "egg".equals(suffix))) {
        // a .zip / .egg file must have its root extracted first
        child = JarFileSystem.getInstance().getJarRootForLocalFile(child);
      }
      if (child != null) {
        // NOTE: Files marked as library sources are not considered part of project source. Since the directory of the project the
        // user is working on is included in PYTHONPATH with many configurations (e.g. virtualenv), we must not mark SDK paths as
        // library sources, only as classes.
        sdkModificator.addRoot(child, OrderRootType.CLASSES);
      }
    }
    else {
      LOG.info("Bogus sys.path entry " + path);
    }
  }
  static List<String> regenerateSkeletons(
    @Nullable ProgressIndicator indicator, Sdk sdk, final String skeletonsPath,
    SkeletonVersionChecker cached_checker,
    Ref<Boolean> migration_flag
  ) {
    List<String> error_list = new SmartList<String>();
    String home_path = sdk.getHomePath();
    final String parent_dir = new File(home_path).getParent();
    final File skel_dir = new File(skeletonsPath);
    if (!skel_dir.exists()) skel_dir.mkdirs();
    final String readable_path = shortenDirName(home_path);

    if (indicator != null) {
      indicator.checkCanceled();
      indicator.setText(String.format("Querying skeleton generator for %s...", readable_path));
      indicator.setText2("");
    }
    // get generator version and binary libs list in one go
    final ProcessOutput run_result = SdkUtil.getProcessOutput(parent_dir,
      new String[]{home_path, PythonHelpersLocator.getHelperPath(GENERATOR3), "-L"},
      getVirtualEnvAdditionalEnv(home_path),
      MINUTE
    );
    if (run_result.getExitCode() != 0) {
      StringBuilder sb = new StringBuilder("failed to run ").append(GENERATOR3)
        .append(" for ").append(home_path)
        .append(", exit code ").append(run_result.getExitCode())
        .append(", stderr: \n-----\n");
      for (String err_line : run_result.getStderrLines()) sb.append(err_line).append("\n");
      sb.append("-----");
      throw new InvalidSdkException(sb.toString());
    }
    // stdout contains version in the first line and then the list of binaries
    final List<String> binaries_output = run_result.getStdoutLines();
    if (binaries_output.size() < 1) {
      throw new InvalidSdkException("Empty output from " + GENERATOR3 + " for " + home_path);
    }
    int generator_version = versionFromString(binaries_output.get(0).trim());

    if (indicator != null) {
      indicator.checkCanceled();
      indicator.setText("Reading versions file...");
    }
    SkeletonVersionChecker checker;
    if (cached_checker != null) checker = cached_checker.withDefaultVersionIfUnknown(generator_version);
    else checker = new SkeletonVersionChecker(generator_version);

    // check builtins
    String builtins_fname = getBuiltinsFileName(sdk);
    File builtins_file = new File(skel_dir, builtins_fname);

    Matcher header_matcher = getParseHeader(builtins_file);
    final boolean old_or_non_existing = header_matcher == null || // no file
                                        !header_matcher.matches(); // no version line
    if (migration_flag != null && !migration_flag.get() && old_or_non_existing) {
      migration_flag.set(true);
      Notifications.Bus.notify(
        new Notification(
          "Skeletons", "Converting old skeletons",
          "Skeletons of binary modules seem to be from an older version.<br/>"+
          "These will be fully re-generated, which will take some time, but will happen <i>only once</i>.<br/>"+
          "Next time you open the project, only skeletons of new or updated binary modules will be re-generated.",
          NotificationType.INFORMATION
        )
      );
    }
    if (old_or_non_existing || versionFromString(header_matcher.group(2)) < checker.getBuiltinVersion()) {
      if (indicator != null) {
        indicator.setText("Updating skeletons of builtins for " + readable_path);
        indicator.setText2("");
      }
      generateBuiltinSkeletons(home_path, skeletonsPath);
    }

    if (indicator != null) indicator.setText("Cleaning up skeletons for " + readable_path);
    cleanUpSkeletons(skel_dir, indicator);
    if (indicator != null) indicator.setText2("");

    if (indicator != null) indicator.setText("Updating skeletons for " + readable_path);
    error_list.addAll(updateOrCreateSkeletons(home_path, skeletonsPath, checker, binaries_output, indicator));

    if (indicator != null) indicator.setText("Reloading generated skeletons...");
    VirtualFile skeletonsVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(skeletonsPath);
    assert skeletonsVFile != null;
    skeletonsVFile.refresh(false, true);
    return error_list;
  }

  /**
   * For every existing skeleton file, take its module file name,
   * and remove the skeleton if the module file does not exist.
   * Works recursively starting from dir. Removes dirs that become empty.
   */
  private static void cleanUpSkeletons(final File dir, @Nullable ProgressIndicator indicator) {
    if (indicator != null) {
      indicator.checkCanceled();
      indicator.setText2("Cleaning up skeletons in " + dir.getPath());
    }
    for (File item : dir.listFiles()) {
      if (item.isDirectory()) {
        cleanUpSkeletons(item, indicator);
        // was the dir emptied?
        File[] remaining = item.listFiles();
        if (remaining.length == 1) {
          File last_file = remaining[0];
          if (PyNames.INIT_DOT_PY.equals(last_file.getName()) && last_file.length() == 0) {
            boolean deleted = deleteOrLog(last_file);
            if (deleted) deleteOrLog(item);
          }
        }
      }
      else if (item.isFile()) {
        // clean up an individual file
        if (PyNames.INIT_DOT_PY.equals(item.getName()) && item.length() == 0) continue; // these are versionless
        Matcher header_matcher = getParseHeader(item);
        boolean can_live = header_matcher != null && header_matcher.matches();
        if (can_live) {
          String fname = header_matcher.group(1);
          can_live = fname != null && (SkeletonVersionChecker.BUILTIN_NAME.equals(fname) || new File(fname).exists());
        }
        if (! can_live) deleteOrLog(item);
      }
    }
  }

  private static boolean deleteOrLog(File item) {
    boolean deleted = item.delete();
    if (! deleted) LOG.warn("Failed to delete skeleton file " + item.getAbsolutePath());
    return deleted;
  }

  static final Pattern ourVersionLinePat = Pattern.compile("# from (\\S+) by generator (\\S+)\\s*");

  @Nullable
  private static Matcher getParseHeader(File infile) {
    try {
      Reader input = new FileReader(infile);
      LineNumberReader lines = new LineNumberReader(input);
      try {
        String line = null;
        for (int i=0; i < 3; i+=1) { // read three lines, skip first two
          line = lines.readLine();
          if (line == null) return null;
        }
        return ourVersionLinePat.matcher(line);
      }
      finally {
        lines.close();
      }
    }
    catch (IOException ignore) {}
    return null;
  }

  private static String getSkeletonsPath(String bin_path) {
    String sep = File.separator;
    return getSkeletonsRootPath() + sep + FileUtil.toSystemIndependentName(bin_path).hashCode() + sep;
  }

  public static String getSkeletonsRootPath() {
    return PathManager.getSystemPath() + File.separator + SKELETON_DIR_NAME;
  }

  @Nullable
  public List<String> getSysPath(String bin_path) {
    String working_dir = new File(bin_path).getParent();
    Application application = ApplicationManager.getApplication();
    if (application != null && !application.isUnitTestMode()) {
      final List<String> paths = getSysPathsFromScript(bin_path);
      myCachedSysPath = paths;
      if (paths == null) throw new InvalidSdkException("Failed to determine Python's sys.path value");
      myCachedSysPath = Collections.unmodifiableList(paths);
      return paths;
    }
    else { // mock sdk
      List<String> ret = new ArrayList<String>(1);
      ret.add(working_dir);
      return ret;
    }
  }

  @Nullable
  public List<String> getCachedSysPath(String bin_path) {
    if (myCachedSysPath != null) return myCachedSysPath;
    else return getSysPath(bin_path);
  }

  protected static boolean checkSuccess(ProcessOutput run_result) {
    if (run_result.getExitCode() != 0) {
      LOG.error(run_result.getStderr() + (run_result.isTimeout()? "\nTimed out" : "\nExit code " + run_result.getExitCode()));
      return false;
    }
    return true;
  }

  @Nullable
  protected static List<String> getSysPathsFromScript(String bin_path) {
    String scriptFile = PythonHelpersLocator.getHelperPath("syspath.py");
    // to handle the situation when PYTHONPATH contains ., we need to run the syspath script in the
    // directory of the script itself - otherwise the dir in which we run the script (e.g. /usr/bin) will be added to SDK path
    String[] add_environment = getVirtualEnvAdditionalEnv(bin_path);
    final ProcessOutput run_result = SdkUtil.getProcessOutput(
      new File(scriptFile).getParent(),
      new String[]{bin_path, scriptFile},
      add_environment, MINUTE
    );
    return checkSuccess(run_result) ? run_result.getStdoutLines() : null;
  }

  // Returns a piece of env good as additional env for getProcessOutput.
  @Nullable
  private static String[] getVirtualEnvAdditionalEnv(String bin_path) {
    File virtualenv_root = getVirtualEnvRoot(bin_path);
    String[] add_environment = null;
    if (virtualenv_root != null) {
      add_environment = new String[] {"PATH=" + virtualenv_root + File.pathSeparator};
    }
    return add_environment;
  }

  @Nullable
  public String getVersionString(final String sdkHome) {
    final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdkHome);
    return flavor != null ? flavor.getVersionString(sdkHome) : null;
  }

  private final static String GENERATOR3 = "generator3.py";

  public static void generateBuiltinSkeletons(String binary_path, final String skeletonsRoot) {
    new File(skeletonsRoot).mkdirs();


    final ProcessOutput run_result = SdkUtil.getProcessOutput(
      new File(binary_path).getParent(),
      new String[]{
        binary_path,
        PythonHelpersLocator.getHelperPath(GENERATOR3),
        "-d", skeletonsRoot, // output dir
        "-b", // for builtins
      },
      getVirtualEnvAdditionalEnv(binary_path), MINUTE *5
    );
    checkSuccess(run_result);
  }

  /**
   * (Re-)generates skeletons for all binary python modules. Up-to-date skeletons are not regenerated.
   * Does one module at a time: slower, but avoids certain conflicts.
   *
   *
   *
   *
   * @param binaryPath   where to find interpreter.
   * @param skeletonsRoot where to put results (expected to exist).
   * @param checker   to check if a skeleton is up to date.
   * @param binaries
   * @param indicator ProgressIndicator to update, or null.
   * @return number of generation errors
   */
  public static List<String> updateOrCreateSkeletons(final String binaryPath,
                                                     final String skeletonsRoot,
                                                     SkeletonVersionChecker checker,
                                                     List<String> binaries, ProgressIndicator indicator) {
    List<String> error_list = new SmartList<String>();
    Iterator<String> bin_iter = binaries.iterator();
    bin_iter.next(); // skip version number. if it weren't here, we'd already die up in regenerateSkeletons()
    while (bin_iter.hasNext()) {
      if (indicator != null) indicator.checkCanceled();

      String line = bin_iter.next(); // line = "mod_name path"
      int cutpos = line.indexOf(' ');
      if (cutpos < 0) LOG.error("Bad binaries line: '" + line + "', SDK " + binaryPath); // but don't die yet
      else {
        String module_name = line.substring(0, cutpos);
        String module_lib_name = line.substring(cutpos+1);
        final String module_path = module_name.replace('.', '/');
        File skeleton_file = new File(skeletonsRoot, module_path + ".py");
        if (!skeleton_file.exists()) {
          skeleton_file = new File(new File(skeletonsRoot, module_path), PyNames.INIT_DOT_PY);
        }
        File lib_file = new File(module_lib_name);
        Matcher matcher = getParseHeader(skeleton_file);
        boolean must_rebuild = true; // guilty unless proven fresh enough
        if (matcher != null && matcher.matches()) {
          int file_version = SkeletonVersionChecker.versionFromString(matcher.group(2));
          int required_version = checker.getRequiredVersion(module_name);
          must_rebuild = file_version < required_version;
        }
        if (!must_rebuild) { // ...but what if the lib was updated?
          must_rebuild = (lib_file.exists() && skeleton_file.exists() && lib_file.lastModified() > skeleton_file.lastModified());
        }
        if (must_rebuild) {
          if (indicator != null) indicator.setText2(module_name);
          LOG.info("Skeleton for " + module_name);
          if (!generateSkeleton(binaryPath, skeletonsRoot, module_name, module_lib_name, Collections.<String>emptyList())) {
            error_list.add(module_name);
          }
        }
      }
    }
    return error_list;
  }

  public static boolean generateSkeleton(String binaryPath, String stubsRoot, String modname, String modfilename, List<String> assemblyRefs) {
    boolean ret = true;
    final String parent_dir = new File(binaryPath).getParent();
    List<String> commandLine = new ArrayList<String>();
    commandLine.add(binaryPath);
    commandLine.add(PythonHelpersLocator.getHelperPath(GENERATOR3));
    commandLine.add("-d");
    commandLine.add(stubsRoot);
    if (!assemblyRefs.isEmpty()) {
      commandLine.add("-c");
      commandLine.add(StringUtil.join(assemblyRefs, ";"));
    }
    if (ApplicationManagerEx.getApplicationEx().isInternal()) {
      commandLine.add("-x");
    }
    commandLine.add(modname);
    if (modfilename != null) commandLine.add(modfilename);

    final ProcessOutput gen_result = SdkUtil.getProcessOutput(
      parent_dir,
      ArrayUtil.toStringArray(commandLine),
      getVirtualEnvAdditionalEnv(binaryPath),
      MINUTE * 10
    );
    if (gen_result.getExitCode() != 0) {
      ret = false;
      StringBuilder sb = new StringBuilder("Skeleton for ");
      sb.append(modname).append(" failed on ").append(binaryPath).append(". stderr: --\n");
      for (String err_line : gen_result.getStderrLines()) sb.append(err_line).append("\n");
      sb.append("--");
      if (ApplicationManagerEx.getApplicationEx().isInternal()) {
        LOG.warn(sb.toString());
      }
      else {
        LOG.info(sb.toString());
      }
    }
    return ret;
  }

  public static List<Sdk> getAllSdks() {
    return ProjectJdkTable.getInstance().getSdksOfType(getInstance());
  }

  @Nullable
  public static Sdk findPythonSdk(@Nullable Module module) {
    if (module == null) return null;
    final Sdk sdk = ModuleRootManager.getInstance(module).getSdk();
    if (sdk != null && sdk.getSdkType() instanceof PythonSdkType) return sdk;
    final Facet[] facets = FacetManager.getInstance(module).getAllFacets();
    for (Facet facet : facets) {
      final FacetConfiguration configuration = facet.getConfiguration();
      if (configuration instanceof PythonFacetSettings) {
        return ((PythonFacetSettings)configuration).getSdk();
      }
    }
    return null;
  }

  @Nullable
  public static Sdk findSdkByPath(@Nullable String path) {
    if (path != null) {
      for (Sdk sdk : getAllSdks()) {
        if (FileUtil.pathsEqual(path, sdk.getHomePath())) {
          return sdk;
        }
      }
    }
    return null;
  }

  public static LanguageLevel getLanguageLevelForSdk(@Nullable Sdk sdk) {
    if (sdk != null) {
      String version = sdk.getVersionString();
      if (version != null) {
        // HACK rewrite in some nicer way?
        if (version.startsWith("Python ") || version.startsWith("Jython ")) {
          String pythonVersion = version.substring("Python ".length());
          return LanguageLevel.fromPythonVersion(pythonVersion);
        }
      }
    }
    return LanguageLevel.getDefault();
  }

  public boolean isRootTypeApplicable(final OrderRootType type) {
    return type == OrderRootType.CLASSES;
  }

  static void refreshSkeletonsOfAllSDKs(final Project project) {
    final Map<String, List<String>> errors = new TreeMap<String, List<String>>();
    final List<String> failed_sdks = new SmartList<String>();
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    final List<Sdk> sdkList = getAllSdks();
    List<String> sdk_errors;
    Ref<Boolean> migration_flag = new Ref<Boolean>(false);
    for (Sdk sdk : sdkList) {
      final String skeletonsPath = findSkeletonsPath(sdk);
      final String homePath = sdk.getHomePath();
      if (skeletonsPath == null) {
        LOG.info("Could not find skeletons path for SDK path " + homePath);
      }
      else {
        LOG.info("Refreshing skeletons for " + homePath);
        try {
          SkeletonVersionChecker checker = new SkeletonVersionChecker(0); // this default version won't be used
          sdk_errors = regenerateSkeletons(indicator, sdk, skeletonsPath, checker, migration_flag);
          if (sdk_errors.size() > 0) {
            String sdk_name = sdk.getName();
            List<String> known_errors = errors.get(sdk_name);
            if (known_errors == null) errors.put(sdk_name, sdk_errors);
            else known_errors.addAll(sdk_errors);
          }
        }
        catch (InvalidSdkException ignore) {
          failed_sdks.add(sdk.getName());
        }
      }
    }
    if (failed_sdks.size() > 0 || errors.size() > 0) {
      int module_errors = 0;
      for (String sdk_name : errors.keySet()) module_errors += errors.get(sdk_name).size();
      String message;
      if (failed_sdks.size() > 0) {
        message = String.format(
            "%d modules failed in %d SDKs, %d SDKs failed <i>completely</i>. <a href='#'>Details...</a>",
            module_errors, errors.size(), failed_sdks.size()
          );
      }
      else {
          message = String.format(
            "%d modules failed in %d SDKs. <a href='#'>Details...</a>",
            module_errors, errors.size()
          );
      }
      Notifications.Bus.notify(
        new Notification(
          "Skeletons", "Some skeletons failed to generate", message,
          NotificationType.WARNING,
          new NotificationListener() {
            @Override
            public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
              new SkeletonErrorsDialog(errors, failed_sdks).setVisible(true);
            }
          }
        ));
    }
  }

  /**
   * Parses required_gen_version file.
   * Efficiently checks file versions against it.
   * Is immutable.
   * <br/>
   * User: dcheryasov
   * Date: 2/23/11 5:32 PM
   */
  static class SkeletonVersionChecker {
    private static final Logger LOG = Logger.getInstance("#com.jetbrains.python.sdk.PythonSdkType.SkeletonVersionChecker");

    final static Pattern ONE_LINE = Pattern.compile("^(?:(\\w+(?:\\.\\w+)*|\\(built-in\\)|\\(default\\))\\s+(\\d+\\.\\d+))?\\s*(?:#.*)?$");

    @NonNls static final String REQUIRED_VERSION_FNAME = "required_gen_version";
    @NonNls static final String DEFAULT_NAME = "(default)"; // version required if a package is not explicitly mentioned
    @NonNls static final String BUILTIN_NAME = "(built-in)"; // version required for built-ins
    private TreeMap<PyQualifiedName, Integer> myExplicitVersion; // versions of regularly named packages
    private Integer myDefaultVersion; // version of (default)
    private Integer myBuiltinsVersion; // version of (built-it)

    /**
     * Creates an instance, loads requirements file.
     */
    public SkeletonVersionChecker(int defaultVersion) {
      myExplicitVersion = createTreeMap();
      myDefaultVersion = defaultVersion;
      load();
    }

    private static TreeMap<PyQualifiedName, Integer> createTreeMap() {
      return new TreeMap<PyQualifiedName, Integer>(new Comparator<PyQualifiedName>() {
        @Override
        public int compare(PyQualifiedName left, PyQualifiedName right) {
          Iterator<String> lefts = left.getComponents().iterator();
          Iterator<String> rights = right.getComponents().iterator();
          while (lefts.hasNext() && rights.hasNext()) {
            int res = lefts.next().compareTo(rights.next());
            if (res != 0) return res;
          }
          if (lefts.hasNext()) return 1;
          if (rights.hasNext()) return -1;
          return 0;  // equal
        }
      });
    }

    private SkeletonVersionChecker(TreeMap<PyQualifiedName, Integer> explicit, Integer builtins) {
      myExplicitVersion = explicit;
      myBuiltinsVersion = builtins;
    }

    /**
     * @param version the new default version
     * @return a shallow copy of this with different default version.
     */
    public SkeletonVersionChecker withDefaultVersionIfUnknown(int version) {
      SkeletonVersionChecker ret = new SkeletonVersionChecker(myExplicitVersion, myBuiltinsVersion);
      ret.myDefaultVersion = myDefaultVersion != 0 ? myDefaultVersion : version;
      return ret;
    }

    private void load() {
      // load the required versions file
      File infile = PythonHelpersLocator.getHelperFile(REQUIRED_VERSION_FNAME);
      try {
        if (infile.canRead()) {
          Reader input = new FileReader(infile);
          LineNumberReader lines = new LineNumberReader(input);
          try {
            String line;
            do {
              line = lines.readLine();
              if (line != null) {
                Matcher matcher = ONE_LINE.matcher(line);
                if (matcher.matches()) {
                  String package_name = matcher.group(1);
                  String ver = matcher.group(2);
                  if (package_name != null) {
                    final int version = versionFromString(ver);
                    if (DEFAULT_NAME.equals(package_name)) {
                      myDefaultVersion = version;
                    }
                    else if (BUILTIN_NAME.equals(package_name)) {
                      myBuiltinsVersion = version;
                    }
                    else {
                      myExplicitVersion.put(PyQualifiedName.fromDottedString(package_name), version);
                    }
                  } // else the whole line is a valid comment, and both catch groups are null
                }
                else LOG.warn(REQUIRED_VERSION_FNAME + ":" + lines.getLineNumber() + " Incorrect line, ignored" );
              }
            } while (line != null);
            if (myBuiltinsVersion == null) {
              myBuiltinsVersion = myDefaultVersion;
              LOG.warn("Assuming default version for built-ins");
            }
            assert (myDefaultVersion != null) : "Default version not known somehow!";
          }
          finally {
            lines.close();
          }
        }
      }
      catch (IOException e) {
        throw new LoadException(e);
      }
    }

    public int getRequiredVersion(String package_name) {
      PyQualifiedName qname = PyQualifiedName.fromDottedString(package_name);
      Map.Entry<PyQualifiedName,Integer> found = myExplicitVersion.floorEntry(qname);
      if (found != null && qname.matchesPrefix(found.getKey())) {
        return found.getValue();
      }
      return myDefaultVersion;
    }

    public int getBuiltinVersion() {
      return myBuiltinsVersion;
    }

    /**
     * Transforms a string like "1.2" into an integer representing it.
     * @param input
     * @return an int representing the version: major number shifted 8 bit and minor number added. or 0 if version can't be parsed.
     */
    public static int versionFromString(final String input) {
      int dot_pos = input.indexOf('.');
      try {
        if (dot_pos > 0) {
          int major = Integer.parseInt(input.substring(0, dot_pos));
          int minor = Integer.parseInt(input.substring(dot_pos+1));
          return (major << 8) + minor;
        }
      }
      catch (NumberFormatException ignore) { }
      return 0;
    }

    public static class LoadException extends RuntimeException {
      public LoadException(Throwable e) {
        super(e);
      }
    }
  }

  public static boolean isStdLib(VirtualFile vFile, Sdk pythonSdk) {
    if (pythonSdk != null) {
      final VirtualFile libDir = PyClassNameIndex.findLibDir(pythonSdk);
      if (libDir != null && VfsUtil.isAncestor(libDir, vFile, false)) {
        final VirtualFile sitePackages = libDir.findChild("site-packages");
        if (sitePackages != null && VfsUtil.isAncestor(sitePackages, vFile, false)) {
          return false;
        }
        return true;
      }
      final VirtualFile skeletonsDir = findSkeletonsDir(pythonSdk);
      if (skeletonsDir != null &&
          vFile.getParent() == skeletonsDir) {   // note: this will pick up some of the binary libraries not in packages
        return true;
      }
    }
    return false;
  }
}

