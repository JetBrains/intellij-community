package com.jetbrains.python.sdk;

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
import java.util.*;

import static com.jetbrains.python.psi.PyUtil.sure;

/**
 * @author yole
 */
public class PythonSdkType extends SdkType {
  private static final Logger LOG = Logger.getInstance("#" + PythonSdkType.class.getName());
  private static final String[] WINDOWS_EXECUTABLE_SUFFIXES = new String[]{"cmd", "exe", "bat", "com"};

  static final int RUN_TIMEOUT = 60 * 1000; // 60 seconds per script invocation is plenty; anything more seems wrong (10 wasn't enough tho).

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
    final String path = findSkeletonsPath(currentSdk);
    if (path != null) {
      File stubs_dir = new File(path);
      if (!stubs_dir.exists()) {
        final ProgressManager progman = ProgressManager.getInstance();
        final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
        final Task.Modal setup_task = new Task.Modal(project, "Setting up library files", false) {

          public void run(@NotNull final ProgressIndicator indicator) {
            try {
              generateBuiltinStubs(currentSdk.getHomePath(), path);
              generateBinarySkeletons(currentSdk.getHomePath(), path, indicator);
            }
            catch (Exception e) {
              LOG.error(e);
            }
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

  @NonNls
  public String getPresentableName() {
    return "Python SDK";
  }

  public void setupSdkPaths(final Sdk sdk) {
    final Ref<SdkModificator> sdkModificatorRef = new Ref<SdkModificator>();
    final Ref<Boolean> success = new Ref<Boolean>();
    success.set(true);
    final ProgressManager progman = ProgressManager.getInstance();
    final Project project = PlatformDataKeys.PROJECT.getData(DataManager.getInstance().getDataContext());
    final Task.Modal setup_task = new Task.Modal(project, "Setting up library files for " + sdk.getName(), false) {

      public void run(@NotNull final ProgressIndicator indicator) {
        try {
          final SdkModificator sdkModificator = sdk.getSdkModificator();
          sdkModificator.removeAllRoots();
          setupSdkPaths(sdkModificator, indicator);
          //sdkModificator.commitChanges() must happen outside, in dispatch thread.
          sdkModificatorRef.set(sdkModificator);
        }
        catch (InvalidSdkException e) {
          success.set(false);
        }
      }

    };
    progman.run(setup_task);
    if (success.get()) {
      if (sdkModificatorRef.get() != null) sdkModificatorRef.get().commitChanges();
    }
    else {
      Messages.showErrorDialog(
        project,
        PyBundle.message("MSG.cant.setup.sdk.$0", FileUtil.toSystemDependentName(sdk.getSdkModificator().getHomePath())),
        PyBundle.message("MSG.title.bad.sdk")
      );
    }
  }


  /**
   * In which root type built-in skeletons are put.
   */
  public static final OrderRootType BUILTIN_ROOT_TYPE = OrderRootType.CLASSES;

  public static void setupSdkPaths(SdkModificator sdkModificator, ProgressIndicator indicator) {
    Application application = ApplicationManager.getApplication();
    boolean not_in_unit_test_mode = (application != null && !application.isUnitTestMode());

    String bin_path = sdkModificator.getHomePath();
    assert bin_path != null;
    String working_dir = new File(bin_path).getParent();
    final String sep = File.separator;
    @NonNls final String stubs_path = PathManager.getSystemPath() + sep + SKELETON_DIR_NAME + sep + bin_path.hashCode() + sep;
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
        final VirtualFile builtins_root = LocalFileSystem.getInstance().refreshAndFindFileByPath(stubs_path);
        assert builtins_root != null;
        sdkModificator.addRoot(builtins_root, BUILTIN_ROOT_TYPE);
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
      generateBinarySkeletons(bin_path, stubs_path, indicator);
    }
  }

  private static List<String> getSysPath(String sdk_path, String bin_path) {
    Application application = ApplicationManager.getApplication();
    if (application != null && !application.isUnitTestMode()) {
      String scriptFile = PythonHelpersLocator.getHelperPath("syspath.py");
      // in order to handle the situation when PYTHONPATH contains ., we need to run the syspath script in the
      // directory of the script itself - otherwise the dir in which we run the script (e.g. /usr/bin) will be added to SDK path
      String[] add_environment = getVirtualEnvAdditionalEnv(bin_path);
      return SdkUtil.getProcessOutput(new File(scriptFile).getParent(),
                                      new String[]{bin_path, scriptFile}, add_environment, RUN_TIMEOUT).getStdoutLines();
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
    final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdkHome);
    return flavor != null ? flavor.getVersionString(sdkHome) : null;
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
      getVirtualEnvAdditionalEnv(binary_path), RUN_TIMEOUT*5
    );
    if (run_result.getExitCode() != 0) {
      LOG.error(run_result.getStderr() + (run_result.isTimeout()? "\nTimed out" : "\nExit code " + run_result.getExitCode()));
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
  public static void generateBinarySkeletons(final String binaryPath, final String stubsRoot, ProgressIndicator indicator) {
    if (indicator != null) {
      indicator.setText("Generating skeletons of binary libs");
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
          generateSkeleton(binaryPath, stubsRoot, modname, Collections.<String>emptyList());
        }
      }
    }
    catch (InvalidSdkException e) {
      StringBuffer sb = new StringBuffer("failed to run ").append(FIND_BINARIES)
        .append(", exit code ").append(run_result.getExitCode())
        .append(", stderr '")
      ;
      for (String err_line : run_result.getStderrLines()) sb.append(err_line).append("\n");
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
      LOG.warn(sb.toString());
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

  public boolean isRootTypeApplicable(final OrderRootType type) {
    return type == OrderRootType.CLASSES;
  }
  
}

class InvalidSdkException extends RuntimeException {
  InvalidSdkException(String s) {
    super(s);
  }
}
