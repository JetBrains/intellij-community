package com.jetbrains.python.sdk;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonHelpersLocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.sdk.SkeletonVersionChecker.versionFromString;

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
  final String mySkeletonsPath;
  private static final int MINUTE = 60 * 1000;
  final static String GENERATOR3 = "generator3.py";

  @NonNls public static final String BLACKLIST_FILE_NAME = ".blacklist";
  final static Pattern BLACKLIST_LINE = Pattern.compile("^([^:]+): (\\d+\\.\\d+) (\\d+)\\s*$");

  private static final Logger LOG = Logger.getInstance("#" + PySkeletonRefresher.class.getName());

  public PySkeletonRefresher(Sdk sdk, String skeletonsPath, ProgressIndicator indicator) {
    myIndicator = indicator;
    mySdk = sdk;
    mySkeletonsPath = skeletonsPath;
  }
  
  private void indicate(String msg, String... args) {
    if (myIndicator != null) {
      myIndicator.checkCanceled();
      myIndicator.setText(String.format(msg, args));
      myIndicator.setText2("");
    }
  }

  private void indicateMinor(String msg, String... args) {
    if (myIndicator != null) {
      myIndicator.setText2(String.format(msg, args));
    }
  }

  private void checkCanceled() {
    if (myIndicator != null) {
      myIndicator.checkCanceled();
    }
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

    Map<String, Pair<Integer, Integer>> blacklist = retrieveBlackList(skel_dir);

    indicate("Querying skeleton generator for %s...", readable_path);
    // get generator version and binary libs list in one go
    final ProcessOutput run_result = SdkUtil.getProcessOutput(parent_dir,
      new String[]{home_path, PythonHelpersLocator.getHelperPath(GENERATOR3), "-L"},
      PythonSdkType.getVirtualEnvAdditionalEnv(home_path),
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

    indicate("Reading versions file...");
    SkeletonVersionChecker checker;
    if (cached_checker != null) checker = cached_checker.withDefaultVersionIfUnknown(generator_version);
    else checker = new SkeletonVersionChecker(generator_version);

    // check builtins
    String builtins_fname = PythonSdkType.getBuiltinsFileName(mySdk);
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
      indicate("Updating skeletons of builtins for {0}", readable_path);
      generateBuiltinSkeletons(home_path, mySkeletonsPath);
    }

    indicate("Cleaning up skeletons for {0}", readable_path);
    cleanUpSkeletons(skel_dir);

    indicate("Updating skeletons for {0}", readable_path);
    error_list.addAll(updateOrCreateSkeletons(home_path, generator_version, checker, binaries_output, blacklist));

    indicate("Reloading generated skeletons...");
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

  private static Map<String, Pair<Integer, Integer>> retrieveBlackList(File skel_dir) {
    Map<String, Pair<Integer, Integer>> ret = new HashMap<String, Pair<Integer, Integer>>();
    File blacklist_file = new File(skel_dir, BLACKLIST_FILE_NAME);
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
                final int version = versionFromString(matcher.group(2));
                if (version > 0) {
                  try {
                    final int timestamp = Integer.parseInt(matcher.group(3));
                    final String filename = matcher.group(1);
                    ret.put(filename, new Pair<Integer, Integer>(version, timestamp));
                    not_parsed = false;
                  }
                  catch (NumberFormatException ignore) {}
                }
              }
              if (not_parsed) LOG.warn("In blacklist at " + skel_dir.getPath() + " strange line '" + line + "'");
            }
          } while (line != null);
        }
        catch (IOException ex) {
          LOG.warn("Failed to read blacklist in " + skel_dir.getPath(), ex);
        }
        finally {
          lines.close();
        }
      }
      catch (IOException ignore) {  }
    }
    return ret;
  }

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
      PythonSdkType.getVirtualEnvAdditionalEnv(binary_path), MINUTE *5
    );
    run_result.checkSuccess(LOG);
  }

  /**
   * For every existing skeleton file, take its module file name,
   * and remove the skeleton if the module file does not exist.
   * Works recursively starting from dir. Removes dirs that become empty.
   */
  private void cleanUpSkeletons(final File dir) {
    indicateMinor("Cleaning up skeletons in {0}", dir.getPath());
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

  /**
   * (Re-)generates skeletons for all binary python modules. Up-to-date skeletons are not regenerated.
   * Does one module at a time: slower, but avoids certain conflicts.
   *
   * @param binaryPath   where to find interpreter.
   * @param skeletonsRoot where to put results (expected to exist).
   * @param checker   to check if a skeleton is up to date.
   * @param binaries
   * @param indicator ProgressIndicator to update, or null.
   * @return number of generation errors
   */
  private List<String> updateOrCreateSkeletons(
    final String binaryPath, int generator_version,
    SkeletonVersionChecker checker, List<String> binaries,
    Map<String, Pair<Integer, Integer>> blacklist
  ) {
    List<String> error_list = new SmartList<String>();
    Iterator<String> bin_iter = binaries.iterator();
    bin_iter.next(); // skip version number. if it weren't here, we'd already die up in regenerateSkeletons()
    while (bin_iter.hasNext()) {
      checkCanceled();

      String line = bin_iter.next(); // line = "mod_name path"
      int cutpos = line.indexOf(' ');
      if (cutpos < 0) LOG.error("Bad binaries line: '" + line + "', SDK " + binaryPath); // but don't die yet
      else {
        String module_name = line.substring(0, cutpos);
        String module_lib_name = line.substring(cutpos+1);
        final String module_path = module_name.replace('.', '/');
        File skeleton_file = new File(mySkeletonsPath, module_path + ".py");
        if (!skeleton_file.exists()) {
          skeleton_file = new File(new File(mySkeletonsPath, module_path), PyNames.INIT_DOT_PY);
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
          // really we can omit these exists() but I keep these to make the logic clearer
        }
        if (blacklist != null) {
          Pair<Integer, Integer> version_info = blacklist.get(module_lib_name);
          if (version_info != null) {
            int failed_generator_version = version_info.getFirst();
            int failed_timestamp = version_info.getSecond();
            must_rebuild &= failed_generator_version < generator_version || failed_timestamp < lib_file.lastModified();
          }
        }
        if (must_rebuild) {
          indicateMinor(module_name);
          LOG.info("Skeleton for " + module_name);
          if (!generateSkeleton(binaryPath, mySkeletonsPath, module_name, module_lib_name, Collections.<String>emptyList())) {
            error_list.add(module_name);
            // TODO: create blacklist out of error_list
          }
        }
      }
    }
    return error_list;
  }

  /**
   * Generates a skeleton for a particular binary module.
   * @param binaryPath path to relevant Python interpreter
   * @param skeletonsRoot root of skeletons dir for relevant SDK
   * @param modname name of the binary module as known to Python (e.g. 'foo.bar')
   * @param modfilename name of file which defines the module, null for built-in modules
   * @param assemblyRefs refs that generator wants to know in .net environment, if applicable
   * @return true if generation completed successfully
   */
  public static boolean generateSkeleton(
    @NotNull String binaryPath, @NotNull String skeletonsRoot, @NotNull String modname,
    @Nullable String modfilename, @Nullable List<String> assemblyRefs
  ) {
    boolean ret = true;
    final String parent_dir = new File(binaryPath).getParent();
    List<String> commandLine = new ArrayList<String>();
    commandLine.add(binaryPath);
    commandLine.add(PythonHelpersLocator.getHelperPath(GENERATOR3));
    commandLine.add("-d");
    commandLine.add(skeletonsRoot);
    if (assemblyRefs != null && !assemblyRefs.isEmpty()) {
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
