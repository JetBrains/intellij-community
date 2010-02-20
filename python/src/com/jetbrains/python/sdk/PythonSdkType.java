package com.jetbrains.python.sdk;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetManager;
import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
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
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.regex.Pattern;

import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * @author yole
 */
public class PythonSdkType extends SdkType {
  private static final Logger LOG = Logger.getInstance("#" + PythonSdkType.class.getName());
  private static final String[] WINDOWS_EXECUTABLE_SUFFIXES = new String[]{"cmd", "exe", "bat", "com"};

  static final int RUN_TIMEOUT = 10 * 1000; // 10 seconds per script invocation is plenty enough; anything more is clearly wrong.

  public static PythonSdkType getInstance() {
    return SdkType.findInstance(PythonSdkType.class);
  }

  public PythonSdkType() {
    super("Python SDK");
  }

  public Icon getIcon() {
    return PythonFileType.INSTANCE.getIcon();
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

  @NonNls static final private String PYTHON_STR = "python";

  @NonNls
  @Nullable
  public String suggestHomePath() {
    TreeSet<String> candidates = new TreeSet<String>(new Comparator<String>() {
      public int compare(String o1, String o2) {
        return findDigits(o1).compareTo(findDigits(o2));
      }
    });
    if (SystemInfo.isWindows) {
      findSubdirInstallations(candidates, "C:\\", PYTHON_STR, "python.exe");
      findSubdirInstallations(candidates, "C:\\Program Files\\", PYTHON_STR, "python.exe");
      findSubdirInstallations(candidates, "C:\\", "jython", "jython.bat");
    }
    else if (SystemInfo.isMac) {
      final String pythonPath = "/Library/Frameworks/Python.framework/Versions";
      VirtualFile rootVDir = LocalFileSystem.getInstance().findFileByPath(pythonPath);
      if (rootVDir != null) {
        for (VirtualFile dir : rootVDir.getChildren()) {
          final String dir_name = dir.getName().toLowerCase();
          if (dir.isDirectory()) {
            if ("Current".equals(dir_name) || dir_name.startsWith("2") || dir_name.startsWith("3") || dir_name.startsWith("jython")) {
              VirtualFile bin_dir = dir.findChild("bin");
              if (bin_dir != null && bin_dir.isDirectory()) {
                VirtualFile python_exe = dir.findChild(PYTHON_STR);
                if (python_exe != null) candidates.add(python_exe.getPath());
                python_exe = dir.findChild("jython"); // maybe it's in bin/
                if (python_exe != null) candidates.add(python_exe.getPath());
              }
              else {
                VirtualFile python_exe = dir.findChild("jython"); // maybe it's not in bin/
                if (python_exe != null) candidates.add(python_exe.getPath());
              }
            }
          }
        }
      }
    }
    else if (SystemInfo.isUnix) {
      VirtualFile rootDir = LocalFileSystem.getInstance().findFileByPath("/usr/bin");
      if (rootDir != null) {
        VirtualFile[] suspects = rootDir.getChildren();
        for (VirtualFile child : suspects) {
          if (!child.isDirectory()) {
            final String child_name = child.getName();
            if (child_name.startsWith(PYTHON_STR) || child_name.startsWith("jython")) {
              candidates.add(child.getPath());
            }
          }
        }
        candidates.add(rootDir.getPath());
      }
    }

    if (candidates.size() > 0) {
      // return latest version
      String[] candidateArray = ArrayUtil.toStringArray(candidates);
      return candidateArray[candidateArray.length - 1];
    }
    return null;
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

  private static void findSubdirInstallations(TreeSet<String> candidates, String rootDir, String dir_prefix, String exe_name) {
    VirtualFile rootVDir = LocalFileSystem.getInstance().findFileByPath("C:\\");
    if (rootVDir != null) {
      for (VirtualFile dir : rootVDir.getChildren()) {
        if (dir.isDirectory() && dir.getName().toLowerCase().startsWith(dir_prefix)) {
          VirtualFile python_exe = dir.findChild(exe_name);
          if (python_exe != null) candidates.add(python_exe.getPath());
        }
      }
    }
  }

  public boolean isValidSdkHome(final String path) {
    return isPythonSdkHome(path) || isJythonSdkHome(path);
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
   * Checks if the path is the name of a Python intepreter.
   *
   * @param path path to check.
   * @return true if paths points to a valid home.
   */
  @NonNls
  private static boolean isPythonSdkHome(final String path) {
    File file = new File(path);
    return file.isFile() && FileUtil.getNameWithoutExtension(file).toLowerCase().startsWith("python");
  }

  /**
   * Checks if the path is the name of a Jython intepreter.
   *
   * @param path path to check.
   * @return true if paths points to a valid home.
   */
  private static boolean isJythonSdkHome(final String path) {
    File file = new File(path);
    return file.isFile() && FileUtil.getNameWithoutExtension(file).toLowerCase().startsWith("jython");
  }

  /**
   * @param path where to look
   * @return Python interpreter executable on the path, or null.
   */
  @Nullable
  @NonNls
  private static File getPythonBinaryPath(final String path) {
    File interpreter = new File(path);
      if (seemsExecutable(interpreter)) {
        return interpreter;
      }
      else {
        return null;
      }
  }

  /**
   * Invokes File.isExectable() by reflection.
   * @param file what to test
   * @return result of invocation, or null if method is not available
   */
  @Nullable
  private static Boolean isExecutableUnder16(File file) {
    try {
      Method method = File.class.getMethod("canExecute", File.class);
      Object ret = method.invoke(file);
      if (ret instanceof Boolean) return ((Boolean)ret);
    }
    catch (NoSuchMethodException ignored) { }
    catch (InvocationTargetException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }
    catch (IllegalAccessException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }
    return null;
  }

  /**
   * @param file what to test
   * @return true if file is known to be executable, or hard to tell; false is file is definitely not executable.
   */
  private static boolean seemsExecutable(File file) {
    if (file.isDirectory()) return false;
    Boolean is_executable = isExecutableUnder16(file); // java 1.6 and above
    if (is_executable != null) return is_executable;
    // but we need to support 1.5 too
    if (SystemInfo.isWindows) {
      try {
        final String path = file.getCanonicalPath();
        for (String suffix : WINDOWS_EXECUTABLE_SUFFIXES) {
          if (file.exists() && path.endsWith(suffix)) return true;
          // TODO: check permissions?
        }
      }
      catch (IOException ignored) { }
    }
    else if (SystemInfo.isUnix) {
      /*
      try {
        ProcessOutput run_result = SdkUtil.getProcessOutput(file.getParent(), new String[] {"/usr/bin/[ -x " + file.getCanonicalPath() + " ]"});
        if (run_result.getExitCode() > 1) {
          LOG.warn(run_result.getStderr());
        }
        return run_result.getExitCode() == 0;
      }
      catch (IOException ex) {
        LOG.warn(ex);
        return false;
      }
      */
    }
    return true;
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
      sure("bin".equals(parent.getName()));
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

  // /home/joe/foo -> ~/foo
  private static String shortenDirName(String path) {
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

  public AdditionalDataConfigurable createAdditionalDataConfigurable(final SdkModel sdkModel, final SdkModificator sdkModificator) {
    return null;
  }

  public void saveAdditionalData(final SdkAdditionalData additionalData, final Element additional) {
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
    // fix skeletons as needed
    String url = findSkeletonsUrl(currentSdk);
    if (url != null) {
      final String path = VfsUtil.urlToPath(url);
      File stubs_dir = new File(path);
      if (!stubs_dir.exists()) {
        final ProgressManager progman = ProgressManager.getInstance();
        final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
        final Task.Modal setup_task = new Task.Modal(project, "Setting up library files", false) {

          public void run(@NotNull final ProgressIndicator indicator) {
            generateBuiltinStubs(currentSdk.getHomePath(), path);
            generateBinaryStubs(currentSdk.getHomePath(), path, indicator);
          }

        };
        progman.run(setup_task);
      }
    }
    return null;
  }

  private boolean switchPathToInterpreter(Sdk currentSdk, String... variants) {
    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    File sdk_file = new File(currentSdk.getHomePath());
    String sdk_name = currentSdk.getName();
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
      Messages.showWarningDialog(project,
        "Failed to convert Python SDK '" + sdk_name + "'\nplease delete and re-create it",
        "Converting Python SDK"
      );
    }
    return success;
  }

  @Nullable
  public static String findSkeletonsUrl(Sdk sdk) {
    final String[] urls = sdk.getRootProvider().getUrls(BUILTIN_ROOT_TYPE);
    for (String url : urls) {
      if (url.contains(SKELETON_DIR_NAME)) {
        return url;
      }
    }
    return null;
  }

  @NonNls
  public String getPresentableName() {
    return "Python SDK";
  }

  public void setupSdkPaths(final Sdk sdk) {
    final Ref<SdkModificator> sdkModificatorRef = new Ref<SdkModificator>();
    final ProgressManager progman = ProgressManager.getInstance();
    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    final Task.Modal setup_task = new Task.Modal(project, "Setting up library files for " + sdk.getName(), false) {

      public void run(@NotNull final ProgressIndicator indicator) {
        final SdkModificator sdkModificator = sdk.getSdkModificator();
        setupSdkPaths(sdkModificator, indicator);
        //sdkModificator.commitChanges() must happen outside, in dispatch thread.
        sdkModificatorRef.set(sdkModificator);
      }

    };
    progman.run(setup_task);
    if (sdkModificatorRef.get() != null) sdkModificatorRef.get().commitChanges(); 
  }


  /**
   * In which root type built-in skeletons are put.
   */
  public static final OrderRootType BUILTIN_ROOT_TYPE = OrderRootType.CLASSES;

  public static void setupSdkPaths(SdkModificator sdkModificator, ProgressIndicator indicator) {
    Application application = ApplicationManager.getApplication();
    boolean not_in_unit_test_mode = (application != null && !application.isUnitTestMode());

    String bin_path = getInterpreterPath(sdkModificator.getHomePath());
    assert bin_path != null;
    String working_dir = new File(bin_path).getParent();
    final String sep = File.separator;
    @NonNls final String stubs_path = PathManager.getSystemPath() + sep + SKELETON_DIR_NAME + sep + working_dir.hashCode() + sep;
    // we have a number of lib dirs, those listed in python's sys.path
    if (indicator != null) {
      indicator.setText("Adding library roots");
    }
    // Add folders from sys.path
    final List<String> paths = getSysPath(working_dir, bin_path);
    if ((paths != null) && paths.size() > 0) {
      // add every path as root.
      for (String path : paths) {
        if (path.indexOf(sep) < 0) continue; // TODO: interpret possible 'special' paths reasonably
        if (indicator != null) {
          indicator.setText2(path);
        }
        VirtualFile child = LocalFileSystem.getInstance().findFileByPath(path);
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
      if (indicator != null) {
        indicator.setText("Generating skeletons of __builtins__");
        indicator.setText2("");
      }
      if (not_in_unit_test_mode) {
        generateBuiltinStubs(bin_path, stubs_path);
        sdkModificator.addRoot(LocalFileSystem.getInstance().refreshAndFindFileByPath(stubs_path), BUILTIN_ROOT_TYPE);
      }
    }
    // Add python-django installed as package in Linux
    if (SystemInfo.isLinux && not_in_unit_test_mode) {
      final VirtualFile file = LocalFileSystem.getInstance().findFileByPath("/usr/lib/python-django");
      if (file != null){
        sdkModificator.addRoot(file, OrderRootType.SOURCES);
        sdkModificator.addRoot(file, OrderRootType.CLASSES);
      }
    }

    if (not_in_unit_test_mode) {
      // regenerate stubs, existing or not
      final File stubs_dir = new File(stubs_path);
      if (!stubs_dir.exists()) stubs_dir.mkdirs();
      generateBinaryStubs(bin_path, stubs_path, indicator);
    }
  }

  private static List<String> getSysPath(String sdk_path, String bin_path) {
    Application application = ApplicationManager.getApplication();
    if (application != null && !application.isUnitTestMode()) {
      @NonNls String script = // a script printing sys.path
        "import sys\n" +
        "import os.path\n" +
        "for x in sys.path:\n" +
        "  if x != os.path.dirname(sys.argv [0]): sys.stdout.write(x+chr(10))"
        ;
      try {
        final File scriptFile = File.createTempFile("script", ".py");
        try {
          PrintStream out = new PrintStream(scriptFile);
          try {
            out.print(script);
          }
          finally {
            out.close();
          }
          String[] add_environment = getVirtualEnvAdditionalEnv(bin_path);
          return SdkUtil.getProcessOutput(sdk_path, new String[]{bin_path, scriptFile.getPath()}, add_environment, RUN_TIMEOUT).getStdoutLines();
        }
        finally {
          FileUtil.delete(scriptFile);
        }
      }
      catch (IOException e) {
        LOG.info(e);
        return Collections.emptyList();
      }
    }
    else { // mock sdk
      List<String> ret = new ArrayList<String>(1);
      ret.add(sdk_path);
      return ret;
    }
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
    final String binaryPath = getInterpreterPath(sdkHome);
    if (binaryPath == null) {
      return null;
    }
    final boolean isJython = isJythonSdkHome(sdkHome);
    @NonNls String version_regexp, version_opt;
    if (isJython) {
      version_regexp = "(Jython \\S+) on .*";
      version_opt = "--version";
    }
    else { // CPython
      version_regexp = "(Python \\S+).*";
      version_opt = "-V";
    }
    Pattern pattern = Pattern.compile(version_regexp);
    String run_dir = new File(binaryPath).getParent();
    final ProcessOutput process_output = SdkUtil.getProcessOutput(run_dir, new String[]{binaryPath, version_opt});
    if (process_output.getExitCode() != 0) {
      LOG.error(process_output.getStderr() + " (exit code " + process_output.getExitCode() + ")");
    }
    String version = SdkUtil.getFirstMatch(process_output.getStderrLines(), pattern);
    return version;
  }

  @Nullable
  public static String getInterpreterPath(final String sdkHome) {
    final File file = getPythonBinaryPath(sdkHome);
    return file != null ? file.getPath() : null;
  }

  private final static String GENERATOR3 = "generator3.py";
  private final static String FIND_BINARIES = "find_binaries.py";

  public static void generateBuiltinStubs(String binary_path, final String stubsRoot) {
    new File(stubsRoot).mkdirs();


    final ProcessOutput run_result = SdkUtil.getProcessOutput(
      new File(binary_path).getParent(),
      new String[]{
        binary_path,
        PythonHelpersLocator.getHelperPath(GENERATOR3),
        "-d", stubsRoot, // output dir
        "-b", // for builtins
        "-u", // for update-only mode
      },
      getVirtualEnvAdditionalEnv(binary_path), RUN_TIMEOUT
    );
    if (run_result.getExitCode() != 0) {
      LOG.error(run_result.getStderr());
    }
  }

  /**
   * (Re-)generates skeletons for all binary python modules. Up-to-date stubs not regenerated.
   * Does one module at a time: slower, but avoids certain conflicts.
   *
   * @param binaryPath   where to find interpreter.
   * @param stubsRoot where to put results (expected to exist).
   * @param indicator ProgressIndicator to update, or null.
   */
  public static void generateBinaryStubs(final String binaryPath, final String stubsRoot, ProgressIndicator indicator) {
    if (indicator != null) {
      indicator.setText("Generating skeletons of binary libs");
    }
    final String parent_dir = new File(binaryPath).getParent();

    final ProcessOutput run_result = SdkUtil.getProcessOutput(parent_dir,
      new String[]{binaryPath, PythonHelpersLocator.getHelperPath(FIND_BINARIES)},
      getVirtualEnvAdditionalEnv(binaryPath),
      RUN_TIMEOUT
    );

    if (run_result.getExitCode() == 0) {
      for (String line : run_result.getStdoutLines()) {
        // line = "mod_name path"
        int cutpos = line.indexOf(' ');
        String modname = line.substring(0, cutpos);
        String mod_fname = modname.replace(".", File.separator); // "a.b.c" -> "a/b/c", no ext
        String fname = line.substring(cutpos + 1);
        //String ext = fname.substring(fname.lastIndexOf('.')); // no way ext is absent
        // check if it's fresh
        File f_orig = new File(fname);
        File f_skel = new File(stubsRoot + File.separator + mod_fname + ".py");
        if (f_orig.lastModified() >= f_skel.lastModified()) {
          // stale skeleton, rebuild
          if (indicator != null) {
            indicator.setText2(modname);
          }
          LOG.info("Skeleton for " + modname);
          final ProcessOutput gen_result = SdkUtil.getProcessOutput(
            parent_dir,
            new String[]{binaryPath, PythonHelpersLocator.getHelperPath(GENERATOR3), "-d", stubsRoot, modname},
            getVirtualEnvAdditionalEnv(binaryPath),
            RUN_TIMEOUT
          );
          if (gen_result.getExitCode() != 0) {
            StringBuffer sb = new StringBuffer("Skeleton for ");
            sb.append(modname).append(" failed. stderr: --");
            for (String err_line : gen_result.getStderrLines()) sb.append(err_line).append("\n");
            sb.append("--");
            LOG.warn(sb.toString());
          }
        }
      }
    }
    else {
      StringBuffer sb = new StringBuffer();
      for (String err_line : run_result.getStderrLines()) sb.append(err_line).append("\n");
      LOG.error("failed to run " + FIND_BINARIES + ", exit code " + run_result.getExitCode() + ", stderr '" + sb.toString() + "'");
    }
  }

  public static List<Sdk> getAllSdks() {
    return ProjectJdkTable.getInstance().getSdksOfType(getInstance());
  }

  @Nullable
  public static Sdk findPythonSdk(Module module) {
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
}
