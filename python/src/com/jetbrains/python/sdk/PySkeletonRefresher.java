package com.jetbrains.python.sdk;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.intellij.util.io.ZipUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonHelpersLocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.sdk.SkeletonVersionChecker.fromVersionString;

/**
 * Handles a refresh of SDK's skeletons.
 * Does all the heavy lifting calling skeleton generator, managing blacklists, etc.
 * One-time, non-reusable instances.
 * <br/>
 * User: dcheryasov
 * Date: 4/15/11 5:38 PM
 */
public class PySkeletonRefresher {
  @Nullable final ProgressIndicator myIndicator;
  final Sdk mySdk;
  String mySkeletonsPath;
  private static final int MINUTE = 60 * 1000;
  final static String GENERATOR3 = "generator3.py";

  @NonNls public static final String BLACKLIST_FILE_NAME = ".blacklist";
  final static Pattern BLACKLIST_LINE = Pattern.compile("^([^=]+) = (\\d+\\.\\d+) (\\d+)\\s*$");
  // we use the equals sign after filename so that we can freely include space in the filename

  private static final Logger LOG = Logger.getInstance("#" + PySkeletonRefresher.class.getName());
  private String myExtraSyspath;
  private VirtualFile myPregeneratedSkeletons;
  private int myGeneratorVersion;
  private Map<String,Pair<Integer,Long>> myBlacklist;
  private SkeletonVersionChecker myVersionChecker;

  /**
   * Creates a new object that refreshes skeletons of given SDK.
   * @param sdk a Python SDK
   * @param skeletonsPath if known; null means 'determine and create as needed'.
   * @param indicator to report progress of long operations
   */
  public PySkeletonRefresher(@NotNull Sdk sdk, @Nullable String skeletonsPath, @Nullable ProgressIndicator indicator) {
    myIndicator = indicator;
    mySdk = sdk;
    mySkeletonsPath = skeletonsPath;
  }
  
  private void indicate(String msg) {
    if (myIndicator != null) {
      myIndicator.checkCanceled();
      myIndicator.setText(msg);
      myIndicator.setText2("");
    }
  }

  private void indicateMinor(String msg) {
    if (myIndicator != null) {
      myIndicator.setText2(msg);
    }
  }

  private void checkCanceled() {
    if (myIndicator != null) {
      myIndicator.checkCanceled();
    }
  }

  private String getExtraSyspath() {
    if (myExtraSyspath == null) {
      VirtualFile[] class_dirs = mySdk.getRootProvider().getFiles(OrderRootType.CLASSES);
      StringBuilder arg_builder = new StringBuilder("\"");
      int i = 0;
      while (i < class_dirs.length) {
        if (i > 0) arg_builder.append(File.pathSeparator);
        if (class_dirs[i].isInLocalFileSystem()) {
          final String pathname = class_dirs[i].getPath();
          if (!mySkeletonsPath.equals(pathname)) arg_builder.append(pathname);
        }
        i += 1;
      }
      arg_builder.append("\"");
      myExtraSyspath = arg_builder.toString();
    }
    return myExtraSyspath;
  }

  /**
   * Creates if needed all path(s) used to store skeletons of its SDK.
   * @return path name of skeleton dir for the SDK, guaranteed to be already created.
   */
  @NotNull
  public String getSkeletonPath() {
    if (mySkeletonsPath == null) {
      mySkeletonsPath = PythonSdkType.getSkeletonsPath(mySdk.getHomePath());
      final File skeletonsDir = new File(mySkeletonsPath);
      if (!skeletonsDir.exists() && !skeletonsDir.mkdirs()) {
        throw new InvalidSdkException("Can't create skeleton dir "+String.valueOf(mySkeletonsPath));
      }
    }
    return mySkeletonsPath;
  }

  List<String> regenerateSkeletons(@Nullable SkeletonVersionChecker cached_checker) {
    return regenerateSkeletons(cached_checker, null);
  }

  List<String> regenerateSkeletons(
    @Nullable SkeletonVersionChecker cached_checker,
    @Nullable Ref<Boolean> migration_flag
  ) {
    List<String> error_list = new SmartList<String>();
    String home_path = mySdk.getHomePath();
    final String parent_dir = new File(home_path).getParent();
    final File skel_dir = new File(mySkeletonsPath);
    if (!skel_dir.exists()) skel_dir.mkdirs();
    final String readable_path = PythonSdkType.shortenDirName(home_path);

    myBlacklist = loadBlacklist();

    indicate(PyBundle.message("sdk.gen.querying.$0", readable_path));
    // get generator version and binary libs list in one go

    long start_time = System.currentTimeMillis();
    final ProcessOutput run_result = SdkUtil.getProcessOutput(
      parent_dir,
      new String[]{home_path, PythonHelpersLocator.getHelperPath(GENERATOR3), "-v", "-L", "-s", getExtraSyspath()},
      PythonSdkType.getVirtualEnvAdditionalEnv(home_path),
      MINUTE * 4 // see PY-3898
    );
    LOG.info("Retrieving binary module list took " + (System.currentTimeMillis() - start_time) + " ms");
    if (run_result.getExitCode() != 0) {
      StringBuilder sb = new StringBuilder("failed to run ").append(GENERATOR3).append(" for ").append(home_path);
      if (run_result.isTimeout()) sb.append(": timed out.");
      else {
        sb.append(", exit code ").append(run_result.getExitCode()).append(", stderr: \n-----\n");
        for (String err_line : run_result.getStderrLines()) sb.append(err_line).append("\n");
        sb.append("-----");
      }
      throw new InvalidSdkException(sb.toString());
    }
    // stdout contains version in the first line and then the list of binaries
    final List<String> binaries_output = run_result.getStdoutLines();
    if (binaries_output.size() < 1) {
      throw new InvalidSdkException("Empty output from " + GENERATOR3 + " for " + home_path);
    }
    myGeneratorVersion = fromVersionString(binaries_output.get(0).trim());
    myPregeneratedSkeletons = findPregeneratedSkeletons();

    indicate(PyBundle.message("sdk.gen.reading.versions.file"));
    if (cached_checker != null) myVersionChecker = cached_checker.withDefaultVersionIfUnknown(myGeneratorVersion);
    else myVersionChecker = new SkeletonVersionChecker(myGeneratorVersion);

    // check builtins
    String builtinsFileName = PythonSdkType.getBuiltinsFileName(mySdk);
    File builtins_file = new File(skel_dir, builtinsFileName);

    Matcher header_matcher = getParseHeader(builtins_file);
    final boolean oldOrNonExisting = header_matcher == null || // no file
                                        !header_matcher.matches(); // no version line
    if (migration_flag != null && !migration_flag.get() && oldOrNonExisting) {
      migration_flag.set(true);
      Notifications.Bus.notify(
        new Notification(
          PythonSdkType.SKELETONS_TOPIC, PyBundle.message("sdk.gen.notify.converting.old.skels"),
          PyBundle.message("sdk.gen.notify.converting.text"),
          NotificationType.INFORMATION
        )
      );
    }

    if (myPregeneratedSkeletons != null && oldOrNonExisting) {
      LOG.info("Unpacking pregenerated skeletons...");
      try {
        ZipUtil.extract(new File(JarFileSystem.getInstance().getVirtualFileForJar(myPregeneratedSkeletons).getPath()),
                        new File(mySkeletonsPath), null);
      }
      catch (IOException e) {
        LOG.info("Error unpacking pregenerated skeletons", e);
      }
    }
    
    if (myPregeneratedSkeletons == null && (oldOrNonExisting || fromVersionString(header_matcher.group(2)) < myVersionChecker.getBuiltinVersion())) {
      indicate(PyBundle.message("sdk.gen.updating.builtins.$0", readable_path));
      generateBuiltinSkeletons();
    }

    if (!oldOrNonExisting) {
      indicate(PyBundle.message("sdk.gen.cleaning.$0", readable_path));
      cleanUpSkeletons(skel_dir);
    }

    indicate(PyBundle.message("sdk.gen.updating.$0", readable_path));
    List<UpdateResult> skel_errors = updateOrCreateSkeletons(binaries_output);

    if (skel_errors.size() > 0) {
      indicateMinor(BLACKLIST_FILE_NAME);
      for (UpdateResult error : skel_errors) {
        if (error.isFresh()) error_list.add(error.getName());
        myBlacklist.put(error.getPath(), new Pair<Integer, Long>(myGeneratorVersion, error.getTimestamp()));
      }
      storeBlacklist(skel_dir, myBlacklist);
    }
    else removeBlacklist(skel_dir);

    indicate(PyBundle.message("sdk.gen.reloading"));
    VirtualFile skeletonsVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(mySkeletonsPath);
    assert skeletonsVFile != null;
    skeletonsVFile.refresh(false, true);
    return error_list;
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

  private Map<String, Pair<Integer, Long>> loadBlacklist() {
    Map<String, Pair<Integer, Long>> ret = new HashMap<String, Pair<Integer, Long>>();
    File blacklist_file = new File(mySkeletonsPath, BLACKLIST_FILE_NAME);
    if (blacklist_file.exists() && blacklist_file.canRead()) {
      Reader input;
      try {
        input = new FileReader(blacklist_file);
        LineNumberReader lines = new LineNumberReader(input);
        try {
          String line;
          do {
            line = lines.readLine();
            if (line != null && line.length() > 0 && line.charAt(0) != '#') { // '#' begins a comment
              Matcher matcher = BLACKLIST_LINE.matcher(line);
              boolean not_parsed = true;
              if (matcher.matches()) {
                final int version = fromVersionString(matcher.group(2));
                if (version > 0) {
                  try {
                    final long timestamp = Long.parseLong(matcher.group(3));
                    final String filename = matcher.group(1);
                    ret.put(filename, new Pair<Integer, Long>(version, timestamp));
                    not_parsed = false;
                  }
                  catch (NumberFormatException ignore) {}
                }
              }
              if (not_parsed) LOG.warn("In blacklist at " + mySkeletonsPath + " strange line '" + line + "'");
            }
          } while (line != null);
        }
        catch (IOException ex) {
          LOG.warn("Failed to read blacklist in " + mySkeletonsPath, ex);
        }
        finally {
          lines.close();
        }
      }
      catch (IOException ignore) {  }
    }
    return ret;
  }

  private static void storeBlacklist(File skel_dir, Map<String, Pair<Integer, Long>> blacklist) {
    File blacklist_file = new File(skel_dir, BLACKLIST_FILE_NAME);
    PrintWriter output;
    try {
      output = new PrintWriter(blacklist_file);
      try {
        output.println("# PyCharm failed to generate skeletons for these modules.");
        output.println("# These skeletons will be re-generated automatically");
        output.println("# when a newer module version or an updated generator becomes available.");
        // each line:   filename = version.string timestamp
        for (String fname : blacklist.keySet()) {
          Pair<Integer, Long> data = blacklist.get(fname);
          output.print(fname);
          output.print(" = ");
          output.print(SkeletonVersionChecker.toVersionString(data.getFirst()));
          output.print(" ");
          output.print(data.getSecond());
          output.println();
        }
      }
      finally {
        output.close();
      }
    }
    catch (IOException ex) {
      LOG.warn("Failed to store blacklist in " + skel_dir.getPath(), ex);
    }
  }

  private static void removeBlacklist(File skel_dir) {
    File blacklist_file = new File(skel_dir, BLACKLIST_FILE_NAME);
    if (blacklist_file.exists()) {
      boolean okay = blacklist_file.delete();
      if (! okay) LOG.warn("Could not delete blacklist file in " + skel_dir.getPath());
    }
  }

  private void generateBuiltinSkeletons() {
    new File(mySkeletonsPath).mkdirs();
    String binaryPath = mySdk.getHomePath();


    long startTime = System.currentTimeMillis();
    final ProcessOutput run_result = SdkUtil.getProcessOutput(
      new File(binaryPath).getParent(),
      new String[]{
        binaryPath,
        PythonHelpersLocator.getHelperPath(GENERATOR3),
        "-d", mySkeletonsPath, // output dir
        "-b", // for builtins
      },
      PythonSdkType.getVirtualEnvAdditionalEnv(binaryPath), MINUTE *5
    );
    run_result.checkSuccess(LOG);
    LOG.info("Rebuilding builtin skeletons took " + (System.currentTimeMillis() - startTime) + " ms");
  }

  /**
   * For every existing skeleton file, take its module file name,
   * and remove the skeleton if the module file does not exist.
   * Works recursively starting from dir. Removes dirs that become empty.
   */
  private void cleanUpSkeletons(final File dir) {
    indicateMinor(dir.getPath());
    for (File item : dir.listFiles()) {
      if (item.isDirectory()) {
        cleanUpSkeletons(item);
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
        final String item_name = item.getName();
        if (PyNames.INIT_DOT_PY.equals(item_name) && item.length() == 0) continue; // these are versionless
        if (BLACKLIST_FILE_NAME.equals(item_name)) continue; // don't touch the blacklist
        Matcher header_matcher = getParseHeader(item);
        boolean can_live = header_matcher != null && header_matcher.matches();
        if (can_live) {
          String source_name = header_matcher.group(1);
          can_live = source_name != null && (SkeletonVersionChecker.BUILTIN_NAME.equals(source_name) || new File(source_name).exists());
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

  private static class UpdateResult {
    private final String myPath;
    private final String myName;
    private final long myTimestamp;

    public boolean isFresh() {
      return myIsFresh;
    }

    private final boolean myIsFresh;

    private UpdateResult(String name, String path, long timestamp, boolean fresh) {
      myName = name;
      myPath = path;
      myTimestamp = timestamp;
      myIsFresh = fresh;
    }

    public String getName() {
      return myName;
    }

    public String getPath() {
      return myPath;
    }

    public Long getTimestamp() {
      return myTimestamp;
    }
  }

  /**
   * (Re-)generates skeletons for all binary python modules. Up-to-date skeletons are not regenerated.
   * Does one module at a time: slower, but avoids certain conflicts.
   *
   * @param binaryPath   where to find interpreter.
   * @param checker   to check if a skeleton is up to date.
   * @param binaries  output of generator3 -L, list of prospective binary modules
   * @return blacklist data; whatever was not generated successfully is put here.
   */
  private List<UpdateResult> updateOrCreateSkeletons(List<String> binaries) {
    final List<UpdateResult> error_list = new SmartList<UpdateResult>();
    final Iterator<String> bin_iter = binaries.iterator();
    bin_iter.next(); // skip version number. if it weren't here, we'd already die up in regenerateSkeletons()
    final int count = Math.max(0, binaries.size() - 1);
    int i = 0;
    while (bin_iter.hasNext()) {
      checkCanceled();
      if (myIndicator != null) {
        myIndicator.setFraction((double)i / count);
      }
      final String line = bin_iter.next(); // line = "mod_name path"
      int cutpos = line.indexOf(' ');
      if (cutpos < 0) LOG.error("Bad binaries line: '" + line + "', SDK " + mySdk.getHomePath()); // but don't die yet
      else {
        String moduleName = line.substring(0, cutpos);
        String module_lib_name = line.substring(cutpos+1);
        if (updateOrCreateSkeleton(moduleName, module_lib_name, error_list)) continue;
      }
      i++;
    }
    return error_list;
  }

  private boolean updateOrCreateSkeleton(String moduleName, String moduleLibName, List<UpdateResult> error_list) {
    final String modulePath = moduleName.replace('.', '/');
    String skeletonPath = getSkeletonPath(); // will create dirs as needed
    File skeleton_file = new File(skeletonPath, modulePath + ".py");
    if (!skeleton_file.exists()) {
      skeleton_file = new File(new File(skeletonPath, modulePath), PyNames.INIT_DOT_PY);
    }
    File lib_file = new File(moduleLibName);
    Matcher matcher = getParseHeader(skeleton_file);
    boolean must_rebuild = true; // guilty unless proven fresh enough
    if (matcher != null && matcher.matches()) {
      int file_version = SkeletonVersionChecker.fromVersionString(matcher.group(2));
      int required_version = myVersionChecker.getRequiredVersion(moduleName);
      must_rebuild = file_version < required_version;
    }
    final long lib_file_timestamp = lib_file.lastModified();
    if (!must_rebuild) { // ...but what if the lib was updated?
      must_rebuild = (lib_file.exists() && skeleton_file.exists() && lib_file_timestamp > skeleton_file.lastModified());
      // really we can omit both exists() calls but I keep these to make the logic clear
    }
    if (myBlacklist != null) {
      Pair<Integer, Long> version_info = myBlacklist.get(moduleLibName);
      if (version_info != null) {
        int failed_generator_version = version_info.getFirst();
        long failed_timestamp = version_info.getSecond();
        must_rebuild &= failed_generator_version < myGeneratorVersion || failed_timestamp < lib_file_timestamp;
        if (! must_rebuild) { // we're still failing to rebuild, it, keep it in blacklist
          error_list.add(new UpdateResult(moduleName, moduleLibName, lib_file_timestamp, false));
        }
      }
    }
    if (must_rebuild) {
      indicateMinor(moduleName);
      if (myPregeneratedSkeletons != null && copyPregeneratedSkeleton(moduleName)) {
        return true;
      }
      LOG.info("Skeleton for " + moduleName);
      if (!generateSkeleton(moduleName, moduleLibName, null)) { // NOTE: are assembly refs always empty for built-ins?
        error_list.add(new UpdateResult(moduleName, moduleLibName, lib_file_timestamp, true));
      }
    }
    return false;
  }

  private boolean copyPregeneratedSkeleton(String moduleName) {
    File targetDir;
    final String modulePath = moduleName.replace('.', '/');
    File skeletonsDir = new File(getSkeletonPath());
    VirtualFile pregenerated = myPregeneratedSkeletons.findFileByRelativePath(modulePath + ".py");
    if (pregenerated == null) {
      pregenerated = myPregeneratedSkeletons.findFileByRelativePath(modulePath + "/" + PyNames.INIT_DOT_PY);
      targetDir = new File(skeletonsDir, modulePath);
    }
    else {
      int pos = modulePath.lastIndexOf('/');
      if (pos < 0) {
        targetDir = skeletonsDir;
      }
      else {
        final String moduleParentPath = modulePath.substring(0, pos);
        targetDir = new File(skeletonsDir, moduleParentPath);
      }
    }
    if (pregenerated != null && (targetDir.exists() || targetDir.mkdirs())) {
      LOG.info("Pregenerated skeleton for " + moduleName);
      File target = new File(targetDir, pregenerated.getName());
      try {
        FileOutputStream fos = new FileOutputStream(target);
        try {
          FileUtil.copy(pregenerated.getInputStream(), fos);
        }
        finally {
          fos.close();
        }
      }
      catch (IOException e) {
        LOG.info("Error copying pregenerated skeleton", e);
        return false;
      }
      return true;
    }
    return false;
  }

  @Nullable
  private VirtualFile findPregeneratedSkeletons() {
    final File root = findPregeneratedSkeletonsRoot();
    if (root == null) {
      return null;
    }
    LOG.info("Pregenerated skeletons root is " + root);
    String version = mySdk.getVersionString().toLowerCase().replace(" ", "-");
    File f;
    if (SystemInfo.isMac) {
      String osVersion = SystemInfo.OS_VERSION;
      int dot = osVersion.indexOf('.');
      if (dot >= 0) {
        int secondDot = osVersion.indexOf('.', dot+1);
        if (secondDot >= 0) {
          osVersion = osVersion.substring(0, secondDot);
        }          
      }
      f = new File(root, "skeletons-mac-" + myGeneratorVersion + "-" + osVersion + "-" + version + ".zip");
    }
    else {
      String os = SystemInfo.isWindows ? "win" : "nix";
      f = new File(root, "skeletons-" + os + "-" + myGeneratorVersion + "-" + version + ".zip");
    }
    if (f.exists()) {
      LOG.info("Found pregenerated skeletons at " + f.getPath());
      final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
      if (virtualFile == null) {
        LOG.info("Could not find pregenerated skeletons in VFS");
        return null;
      }
      return JarFileSystem.getInstance().getJarRootForLocalFile(virtualFile);
    }
    else {
      LOG.info("Not found pregenerated skeletons at " + f.getPath());
      return null;
    }
  }

  @Nullable
  private static File findPregeneratedSkeletonsRoot() {
    final String path = PathManager.getHomePath();
    LOG.info("Home path is " + path);
    File f = new File(path, "python/skeletons");  // from sources
    if (f.exists()) return f;
    f = new File(path, "skeletons");              // compiled binary
    if (f.exists()) return f;
    return null;
  }

  /**
   * Generates a skeleton for a particular binary module.
   *
   * @param modname name of the binary module as known to Python (e.g. 'foo.bar')
   * @param modfilename name of file which defines the module, null for built-in modules
   * @param assemblyRefs refs that generator wants to know in .net environment, if applicable
   * @return true if generation completed successfully
   */
  public boolean generateSkeleton(
    @NotNull String modname, @Nullable String modfilename, @Nullable List<String> assemblyRefs
  ) {
    boolean ret = true;
    String binaryPath = mySdk.getHomePath();
    final String parent_dir = new File(binaryPath).getParent();
    List<String> commandLine = new ArrayList<String>();
    commandLine.add(binaryPath);
    commandLine.add(PythonHelpersLocator.getHelperPath(GENERATOR3));
    commandLine.add("-d");
    commandLine.add(getSkeletonPath());
    if (assemblyRefs != null && !assemblyRefs.isEmpty()) {
      commandLine.add("-c");
      commandLine.add(StringUtil.join(assemblyRefs, ";"));
    }
    if (ApplicationManagerEx.getApplicationEx().isInternal()) {
      commandLine.add("-x");
    }
    commandLine.add("-s");
    commandLine.add(getExtraSyspath());
    commandLine.add(modname);
    if (modfilename != null) commandLine.add(modfilename);

    final ProcessOutput gen_result = SdkUtil.getProcessOutput(
      parent_dir,
      ArrayUtil.toStringArray(commandLine),
      PythonSdkType.getVirtualEnvAdditionalEnv(binaryPath),
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
}
