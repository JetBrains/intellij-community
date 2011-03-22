package com.jetbrains.python.sdk;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetManager;
import com.intellij.ide.DataManager;
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
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonHelpersLocator;
import com.jetbrains.python.facet.PythonFacetSettings;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.impl.PyBuiltinCache;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;
import java.io.FilenameFilter;
import java.util.*;
import java.util.regex.Pattern;

import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * @author yole
 */
public class PythonSdkType extends SdkType {
  private static final Logger LOG = Logger.getInstance("#" + PythonSdkType.class.getName());
  private static final String[] WINDOWS_EXECUTABLE_SUFFIXES = new String[]{"cmd", "exe", "bat", "com"};

  static final int RUN_TIMEOUT = 60 * 1000; // 60 seconds per script invocation is plenty; anything more seems wrong (10 wasn't enough tho).
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
      if (existing_path == null || existing_path.indexOf(virtualenv_bin) < 0) {
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

      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          sdkModificator.removeAllRoots();
          updateSdkRootsFromSysPath(sdkModificator, indicator, (PythonSdkType)sdk.getSdkType());
          if (!ApplicationManager.getApplication().isUnitTestMode() && PythonSdkUpdater.skeletonsUpToDate()) {
            // if skeletons not up to date, they'll be built by PythonSdkUpdater
            generateSkeletons(indicator, sdk.getHomePath(), getSkeletonsPath(sdk.getHomePath()));
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
        if (path.indexOf(sep) < 0) continue; // TODO: interpret possible 'special' paths reasonably
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
        sdkModificator.addRoot(file, OrderRootType.SOURCES);
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
        sdkModificator.addRoot(child, OrderRootType.SOURCES);
        sdkModificator.addRoot(child, OrderRootType.CLASSES);
      }
    }
    else {
      LOG.info("Bogus sys.path entry " + path);
    }
  }

  public static void generateSkeletons(@Nullable ProgressIndicator indicator, String homePath, final String skeletonsPath) {
    final File stubs_dir = new File(skeletonsPath);
    if (!stubs_dir.exists()) stubs_dir.mkdirs();

    if (indicator != null) {
      indicator.setText("Generating skeletons of __builtins__ for interpreter " + homePath);
      indicator.setText2("");
    }
    generateBuiltinSkeletons(homePath, skeletonsPath);

    // regenerate stubs, existing or not
    generateBinarySkeletons(homePath, skeletonsPath, indicator);

    VirtualFile skeletonsVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(skeletonsPath);
    assert skeletonsVFile != null;
    if (indicator != null) {
      indicator.setText("Reloading generated skeletons...");
    }
    skeletonsVFile.refresh(false, true);
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
      add_environment, RUN_TIMEOUT
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
  private final static String FIND_BINARIES = "find_binaries.py";

  public static void generateBuiltinSkeletons(String binary_path, final String skeletonsRoot) {
    new File(skeletonsRoot).mkdirs();


    final ProcessOutput run_result = SdkUtil.getProcessOutput(
      new File(binary_path).getParent(),
      new String[]{
        binary_path,
        PythonHelpersLocator.getHelperPath(GENERATOR3),
        "-d", skeletonsRoot, // output dir
        "-b", // for builtins
        "-u", // for update-only mode
      },
      getVirtualEnvAdditionalEnv(binary_path), RUN_TIMEOUT*5
    );
    checkSuccess(run_result);
  }

  /**
   * (Re-)generates skeletons for all binary python modules. Up-to-date stubs not regenerated.
   * Does one module at a time: slower, but avoids certain conflicts.
   *
   * @param binaryPath   where to find interpreter.
   * @param skeletonsRoot where to put results (expected to exist).
   * @param indicator ProgressIndicator to update, or null.
   */
  public static void generateBinarySkeletons(final String binaryPath, final String skeletonsRoot, ProgressIndicator indicator) {
    if (indicator != null) {
      indicator.setText("Generating skeletons of binary libs for interpreter " + binaryPath);
    }
    final String parent_dir = new File(binaryPath).getParent();

    final ProcessOutput run_result = SdkUtil.getProcessOutput(parent_dir,
      new String[]{binaryPath, PythonHelpersLocator.getHelperPath(FIND_BINARIES)},
      getVirtualEnvAdditionalEnv(binaryPath),
      RUN_TIMEOUT
    );

   try {
     if (run_result.getExitCode() != 0) throw new InvalidSdkException("Exit code");
      for (String line : run_result.getStdoutLines()) {
        // line = "mod_name path"
        int cutpos = line.indexOf(' ');
        if (cutpos < 0) throw new InvalidSdkException("Bad output");
        String modname = line.substring(0, cutpos);
        //String ext = fname.substring(fname.lastIndexOf('.')); // no way ext is absent
        // stale skeleton, rebuild
        if (indicator != null) {
          indicator.setText2(modname);
        }
        LOG.info("Skeleton for " + modname);
        generateSkeleton(binaryPath, skeletonsRoot, modname, Collections.<String>emptyList());
      }
    }
    catch (InvalidSdkException e) {
      StringBuffer sb = new StringBuffer("failed to run ").append(FIND_BINARIES)
        .append(", exit code ").append(run_result.getExitCode())
        .append(", stderr '")
      ;
      for (String err_line : run_result.getStderrLines()) sb.append(err_line).append("'\n");
      throw new InvalidSdkException(sb.toString());
    }
  }

  public static void generateSkeleton(String binaryPath, String stubsRoot, String modname, List<String> assemblyRefs) {
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

    final ProcessOutput gen_result = SdkUtil.getProcessOutput(
      parent_dir,
      commandLine.toArray(new String[commandLine.size()]),
      getVirtualEnvAdditionalEnv(binaryPath),
      RUN_TIMEOUT*10
    );
    if (gen_result.getExitCode() != 0) {
      StringBuffer sb = new StringBuffer("Skeleton for ");
      sb.append(modname).append(" failed. stderr: --");
      for (String err_line : gen_result.getStderrLines()) sb.append(err_line).append("\n");
      sb.append("--");
      if (ApplicationManagerEx.getApplicationEx().isInternal()) {
        LOG.error(sb.toString());
      }
      else {
        LOG.warn(sb.toString());
      }
    }
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

}

class InvalidSdkException extends RuntimeException {
  InvalidSdkException(String s) {
    super(s);
  }
}
