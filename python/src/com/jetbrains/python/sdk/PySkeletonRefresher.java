package com.jetbrains.python.sdk;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.SmartList;
import com.intellij.util.io.ZipUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.remote.PythonRemoteInterpreterManager;
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
  private static final Logger LOG = Logger.getInstance("#" + PySkeletonRefresher.class.getName());


  private @Nullable final ProgressIndicator myIndicator;
  private final Sdk mySdk;
  private String mySkeletonsPath;

  @NonNls public static final String BLACKLIST_FILE_NAME = ".blacklist";
  private final static Pattern BLACKLIST_LINE = Pattern.compile("^([^=]+) = (\\d+\\.\\d+) (\\d+)\\s*$");
  // we use the equals sign after filename so that we can freely include space in the filename

  private final static Pattern OUR_VERSION_LINE = Pattern.compile("# from (\\S+) by generator (\\S+)\\s*");


  private String myExtraSyspath;
  private VirtualFile myPregeneratedSkeletons;
  private int myGeneratorVersion;
  private Map<String, Pair<Integer, Long>> myBlacklist;
  private SkeletonVersionChecker myVersionChecker;

  private PySkeletonGenerator mySkeletonsGenerator;

  /**
   * Creates a new object that refreshes skeletons of given SDK.
   *
   * @param sdk           a Python SDK
   * @param skeletonsPath if known; null means 'determine and create as needed'.
   * @param indicator     to report progress of long operations
   */
  public PySkeletonRefresher(@NotNull Sdk sdk, @Nullable String skeletonsPath, @Nullable ProgressIndicator indicator)
    throws InvalidSdkException {
    myIndicator = indicator;
    mySdk = sdk;
    mySkeletonsPath = skeletonsPath;
    if (PySdkUtil.isRemote(sdk) && PythonRemoteInterpreterManager.getInstance() != null) {
      //noinspection ConstantConditions
      mySkeletonsGenerator = PythonRemoteInterpreterManager.getInstance().createRemoteSkeletonGenerator(getSkeletonsPath(), sdk);
    }
    else {
      mySkeletonsGenerator = new PySkeletonGenerator(getSkeletonsPath());
    }
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

  private static String calculateExtraSysPath(@NotNull Sdk sdk, @Nullable String skeletonsPath) {
    final VirtualFile[] classDirs = sdk.getRootProvider().getFiles(OrderRootType.CLASSES);
    final StringBuilder builder = new StringBuilder("\"");
    int i = 0;
    while (i < classDirs.length) {
      if (i > 0) {
        builder.append(File.pathSeparator);
      }
      if (classDirs[i].isInLocalFileSystem()) {
        final String pathname = classDirs[i].getPath();
        if (pathname != null && !pathname.equals(skeletonsPath)) {
          builder.append(pathname);
        }
      }
      i += 1;
    }
    builder.append("\"");
    return builder.toString();
  }

  /**
   * Creates if needed all path(s) used to store skeletons of its SDK.
   *
   * @return path name of skeleton dir for the SDK, guaranteed to be already created.
   */
  @NotNull
  public String getSkeletonsPath() throws InvalidSdkException {
    if (mySkeletonsPath == null) {
      mySkeletonsPath = PythonSdkType.getSkeletonsPath(PathManager.getSystemPath(), mySdk.getHomePath());
      final File skeletonsDir = new File(mySkeletonsPath);
      if (!skeletonsDir.exists() && !skeletonsDir.mkdirs()) {
        throw new InvalidSdkException("Can't create skeleton dir " + String.valueOf(mySkeletonsPath));
      }
    }
    return mySkeletonsPath;
  }

  @Nullable
  private static Integer getSkeletonVersion(File file) {
    final Matcher headerMatcher = getParseHeader(file);
    if (headerMatcher != null && headerMatcher.matches()) {
      return fromVersionString(headerMatcher.group(2));
    }
    return null;
  }

  List<String> regenerateSkeletons(Project project, @Nullable SkeletonVersionChecker cached_checker,
                                   @Nullable Ref<Boolean> migration_flag) throws InvalidSdkException {
    final List<String> errorList = new SmartList<String>();
    final String home_path = mySdk.getHomePath();
    final String skeletonsPath = getSkeletonsPath();
    final File skeletonsDir = new File(skeletonsPath);
    if (!skeletonsDir.exists()) {
      skeletonsDir.mkdirs();
    }
    final String readable_path = PythonSdkType.shortenDirName(home_path);

    myBlacklist = loadBlacklist();

    indicate(PyBundle.message("sdk.gen.querying.$0", readable_path));
    // get generator version and binary libs list in one go

    final PySkeletonGenerator.ListBinariesResult binaries =
      mySkeletonsGenerator.listBinaries(mySdk, calculateExtraSysPath(mySdk, getSkeletonsPath()));
    myGeneratorVersion = binaries.generatorVersion;
    myPregeneratedSkeletons = findPregeneratedSkeletons();

    indicate(PyBundle.message("sdk.gen.reading.versions.file"));
    if (cached_checker != null) {
      myVersionChecker = cached_checker.withDefaultVersionIfUnknown(myGeneratorVersion);
    }
    else {
      myVersionChecker = new SkeletonVersionChecker(myGeneratorVersion);
    }

    // check builtins
    final String builtinsFileName = PythonSdkType.getBuiltinsFileName(mySdk);
    final File builtinsFile = new File(skeletonsPath, builtinsFileName);

    final boolean oldOrNonExisting = getSkeletonVersion(builtinsFile) == null;

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
      indicate("Unpacking pregenerated skeletons...");
      try {
        final VirtualFile jar = JarFileSystem.getInstance().getVirtualFileForJar(myPregeneratedSkeletons);
        if (jar != null) {
          ZipUtil.extract(new File(jar.getPath()),
                          new File(getSkeletonsPath()), null);
        }
      }
      catch (IOException e) {
        LOG.info("Error unpacking pregenerated skeletons", e);
      }
    }

    if (oldOrNonExisting) {
      final Sdk base = PythonSdkType.getInstance().getVirtualEnvBaseSdk(mySdk);
      if (base != null) {
        indicate("Copying base SDK skeletons for virtualenv...");
        final String baseSkeletonsPath = PythonSdkType.getSkeletonsPath(PathManager.getSystemPath(), base.getHomePath());
        final PySkeletonGenerator.ListBinariesResult baseBinaries =
          mySkeletonsGenerator.listBinaries(base, calculateExtraSysPath(base, baseSkeletonsPath));
        for (Map.Entry<String, File> entry : binaries.modules.entrySet()) {
          final String module = entry.getKey();
          final File binary = entry.getValue();
          final File baseBinary = baseBinaries.modules.get(module);
          final File fromFile = getSkeleton(module, baseSkeletonsPath);
          if (baseBinaries.modules.containsKey(module) &&
              fromFile.exists() &&
              binary.length() == baseBinary.length()) { // Weak binary modules equality check
            final File toFile = fromFile.isDirectory() ?
                                getPackageSkeleton(module, skeletonsPath) :
                                getModuleSkeleton(module, skeletonsPath);
            try {
              FileUtil.copy(fromFile, toFile);
            }
            catch (IOException e) {
              LOG.info("Error copying base virtualenv SDK skeleton for " + module, e);
            }
          }
        }
      }
    }

    final Integer builtinVersion = getSkeletonVersion(builtinsFile);
    if (myPregeneratedSkeletons == null && (builtinVersion == null || builtinVersion < myVersionChecker.getBuiltinVersion())) {
      indicate(PyBundle.message("sdk.gen.updating.builtins.$0", readable_path));
      mySkeletonsGenerator.generateBuiltinSkeletons(mySdk);
    }

    if (!binaries.modules.isEmpty()) {

      indicate(PyBundle.message("sdk.gen.updating.$0", readable_path));
      List<UpdateResult> updateErrors = updateOrCreateSkeletons(binaries.modules);

      if (updateErrors.size() > 0) {
        indicateMinor(BLACKLIST_FILE_NAME);
        for (UpdateResult error : updateErrors) {
          if (error.isFresh()) errorList.add(error.getName());
          myBlacklist.put(error.getPath(), new Pair<Integer, Long>(myGeneratorVersion, error.getTimestamp()));
        }
        storeBlacklist(skeletonsDir, myBlacklist);
      }
      else {
        removeBlacklist(skeletonsDir);
      }
    }

    indicate(PyBundle.message("sdk.gen.reloading"));

    mySkeletonsGenerator.refreshGeneratedSkeletons(project);

    if (!oldOrNonExisting) {
      indicate(PyBundle.message("sdk.gen.cleaning.$0", readable_path));
      cleanUpSkeletons(skeletonsDir);
    }

    return errorList;
  }

  @Nullable
  private static Matcher getParseHeader(File infile) {
    try {
      Reader input = new FileReader(infile);
      LineNumberReader lines = new LineNumberReader(input);
      try {
        String line = null;
        for (int i = 0; i < 3; i += 1) { // read three lines, skip first two
          line = lines.readLine();
          if (line == null) return null;
        }
        return OUR_VERSION_LINE.matcher(line);
      }
      finally {
        lines.close();
      }
    }
    catch (IOException ignore) {
    }
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
                  catch (NumberFormatException ignore) {
                  }
                }
              }
              if (not_parsed) LOG.warn("In blacklist at " + mySkeletonsPath + " strange line '" + line + "'");
            }
          }
          while (line != null);
        }
        catch (IOException ex) {
          LOG.warn("Failed to read blacklist in " + mySkeletonsPath, ex);
        }
        finally {
          lines.close();
        }
      }
      catch (IOException ignore) {
      }
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
      if (!okay) LOG.warn("Could not delete blacklist file in " + skel_dir.getPath());
    }
  }

  /**
   * For every existing skeleton file, take its module file name,
   * and remove the skeleton if the module file does not exist.
   * Works recursively starting from dir. Removes dirs that become empty.
   */
  private void cleanUpSkeletons(final File dir) {
    indicateMinor(dir.getPath());
    final File[] files = dir.listFiles();
    if (files == null) {
      return;
    }
    for (File item : files) {
      if (item.isDirectory()) {
        cleanUpSkeletons(item);
        // was the dir emptied?
        File[] remaining = item.listFiles();
        if (remaining != null && remaining.length == 0) {
          mySkeletonsGenerator.deleteOrLog(item);
        }
        else if (remaining != null && remaining.length == 1) { //clean also if contains only __init__.py
          File last_file = remaining[0];
          if (PyNames.INIT_DOT_PY.equals(last_file.getName()) && last_file.length() == 0) {
            boolean deleted = mySkeletonsGenerator.deleteOrLog(last_file);
            if (deleted) mySkeletonsGenerator.deleteOrLog(item);
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
        if (!can_live) {
          mySkeletonsGenerator.deleteOrLog(item);
        }
      }
    }
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
   * @param modules output of generator3 -L
   * @return blacklist data; whatever was not generated successfully is put here.
   */
  private List<UpdateResult> updateOrCreateSkeletons(Map<String, File> modules) throws InvalidSdkException {
    final List<String> names = new ArrayList<String>(modules.keySet());
    Collections.sort(names);
    final List<UpdateResult> results = new ArrayList<UpdateResult>();
    final int count = names.size();
    for (int i = 0; i < count; i++) {
      checkCanceled();
      if (myIndicator != null) {
        myIndicator.setFraction((double)i / count);
      }
      final String name = names.get(i);
      final File module = modules.get(name);
      if (module != null) {
        updateOrCreateSkeleton(name, module.getPath(), results);
      }
    }
    return results;
  }

  private static File getSkeleton(String moduleName, String skeletonsPath) {
    final File module = getModuleSkeleton(moduleName, skeletonsPath);
    return module.exists() ? module : getPackageSkeleton(moduleName, skeletonsPath);
  }

  private static File getModuleSkeleton(String module, String skeletonsPath) {
    final String modulePath = module.replace('.', '/');
    return new File(skeletonsPath, modulePath + ".py");
  }

  private static File getPackageSkeleton(String pkg, String skeletonsPath) {
    final String packagePath = pkg.replace('.', '/');
    return new File(new File(skeletonsPath, packagePath), PyNames.INIT_DOT_PY);
  }

  private boolean updateOrCreateSkeleton(String moduleName, String moduleLibName,
                                         List<UpdateResult> error_list) throws InvalidSdkException {
    final File skeleton = getSkeleton(moduleName, getSkeletonsPath());
    final File binary = new File(moduleLibName);
    Matcher matcher = getParseHeader(skeleton);
    boolean must_rebuild = true; // guilty unless proven fresh enough
    if (matcher != null && matcher.matches()) {
      int file_version = fromVersionString(matcher.group(2));
      int required_version = myVersionChecker.getRequiredVersion(moduleName);
      must_rebuild = file_version < required_version;
    }
    final long lib_file_timestamp = binary.lastModified();
    if (!must_rebuild) { // ...but what if the lib was updated?
      must_rebuild = (binary.exists() && skeleton.exists() && lib_file_timestamp > skeleton.lastModified());
      // really we can omit both exists() calls but I keep these to make the logic clear
    }
    if (myBlacklist != null) {
      Pair<Integer, Long> version_info = myBlacklist.get(moduleLibName);
      if (version_info != null) {
        int failed_generator_version = version_info.getFirst();
        long failed_timestamp = version_info.getSecond();
        must_rebuild &= failed_generator_version < myGeneratorVersion || failed_timestamp < lib_file_timestamp;
        if (!must_rebuild) { // we're still failing to rebuild, it, keep it in blacklist
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

  private boolean copyPregeneratedSkeleton(String moduleName) throws InvalidSdkException {
    File targetDir;
    final String modulePath = moduleName.replace('.', '/');
    File skeletonsDir = new File(getSkeletonsPath());
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
    final String versionString = mySdk.getVersionString();
    if (versionString == null) {
      return null;
    }
    String version = versionString.toLowerCase().replace(" ", "-");
    File f;
    if (SystemInfo.isMac) {
      String osVersion = SystemInfo.OS_VERSION;
      int dot = osVersion.indexOf('.');
      if (dot >= 0) {
        int secondDot = osVersion.indexOf('.', dot + 1);
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
   * @param modname      name of the binary module as known to Python (e.g. 'foo.bar')
   * @param modfilename  name of file which defines the module, null for built-in modules
   * @param assemblyRefs refs that generator wants to know in .net environment, if applicable
   * @return true if generation completed successfully
   */
  public boolean generateSkeleton(@NotNull String modname, @Nullable String modfilename,
                                  @Nullable List<String> assemblyRefs) throws InvalidSdkException {
    return mySkeletonsGenerator.generateSkeleton(modname, modfilename, assemblyRefs, getExtraSyspath(), mySdk.getHomePath());
  }


  private String getExtraSyspath() {
    if (myExtraSyspath == null) {
      myExtraSyspath = calculateExtraSysPath(mySdk, mySkeletonsPath);
    }
    return myExtraSyspath;
  }
}
